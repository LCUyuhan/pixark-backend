package com.yeyuchunlan.yupicturebackend.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yeyuchunlan.yupicturebackend.api.aiimageedit.AliyunImageEditApi;
import com.yeyuchunlan.yupicturebackend.api.aiimageedit.model.AiEditCreateTaskResult;
import com.yeyuchunlan.yupicturebackend.api.aiimageedit.model.AiEditQueryTaskResult;
import com.yeyuchunlan.yupicturebackend.api.aiimageedit.model.AliyunImageEditRequest;
import com.yeyuchunlan.yupicturebackend.config.AiImageEditProperties;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.exception.ThrowUtils;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.AiImageEditCreateRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.AiImageEditSaveRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.Picture;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.enums.AiImageEditFunctionEnum;
import com.yeyuchunlan.yupicturebackend.model.enums.AiImageEditStyleEnum;
import com.yeyuchunlan.yupicturebackend.model.enums.AiImageEditTaskStatusEnum;
import com.yeyuchunlan.yupicturebackend.model.vo.AiImageEditBaseImageVO;
import com.yeyuchunlan.yupicturebackend.model.vo.AiImageEditQuotaVO;
import com.yeyuchunlan.yupicturebackend.model.vo.AiImageEditStyleVO;
import com.yeyuchunlan.yupicturebackend.model.vo.AiImageEditTaskVO;
import com.yeyuchunlan.yupicturebackend.model.vo.PictureVO;
import com.yeyuchunlan.yupicturebackend.service.AiImageEditService;
import com.yeyuchunlan.yupicturebackend.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 图片编辑服务实现。
 * 链路：校验图片与权限 -> 占用日额度 -> 下发百炼异步任务 -> 记录任务归属 -> 前端轮询 -> 服务端取回结果并复用上传链路入库。
 * 任务归属与额度都放在 Redis（登录态本身也存在 Redis），不新增任务表。
 */
@Slf4j
@Service
public class AiImageEditServiceImpl implements AiImageEditService {

    /**
     * 任务归属记录：{userId, pictureId, function, resultUrl}
     */
    private static final String TASK_KEY_PREFIX = "yupicture:aiEdit:task:";

    /**
     * 单用户当日已提交任务数
     */
    private static final String QUOTA_KEY_PREFIX = "yupicture:aiEdit:quota:";

    /**
     * 百炼侧 task_id 与结果图片 URL 的最长保留时间
     */
    private static final int ALIYUN_RESULT_KEEP_HOURS = 24;

    private static final int MAX_PROMPT_LENGTH = 800;

    /**
     * 风格预设模板里的占位符（如「把%s变成冰雕风格」），前端必须替换成作用对象
     */
    private static final String PROMPT_PLACEHOLDER = "%s";

    private static final int MIN_SIDE_PIXEL = 512;

    private static final int MAX_SIDE_PIXEL = 4096;

    private static final long MAX_PIC_SIZE = 10 * 1024 * 1024L;

    private static final float MIN_EXPAND_SCALE = 1.0f;

    private static final float MAX_EXPAND_SCALE = 2.0f;

    private static final float MIN_STRENGTH = 0.0f;

    private static final float MAX_STRENGTH = 1.0f;

    private static final float DEFAULT_STRENGTH = 0.5f;

    private static final int MIN_UPSCALE_FACTOR = 1;

    private static final int MAX_UPSCALE_FACTOR = 4;

    /**
     * 蒙版图像 base64 长度上限，约等于解码后 10MB（接口限制图像不超过 10MB）
     */
    private static final int MAX_MASK_BASE64_LENGTH = 14_000_000;

    /**
     * 后端代取原图字节的超时（毫秒），只读底图用
     */
    private static final int BASE_IMAGE_TIMEOUT_MILLIS = 15_000;

    /**
     * 接口支持的输入图格式（gif 不在其中）
     */
    private static final List<String> ALLOWED_INPUT_FORMATS =
            Arrays.asList("jpg", "jpeg", "png", "bmp", "tiff", "webp");

    @Resource
    private AiImageEditProperties aiImageEditProperties;

    @Resource
    private AliyunImageEditApi aliyunImageEditApi;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public AiImageEditTaskVO createTask(AiImageEditCreateRequest aiImageEditCreateRequest, User loginUser) {
        ThrowUtils.throwIf(!aiImageEditProperties.isEnabled(), ErrorCode.OPERATION_ERROR, "AI 图片编辑功能未开启");
        ThrowUtils.throwIf(aiImageEditCreateRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Long pictureId = aiImageEditCreateRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片 id 不合法");

        // 1. 图片存在且有权限（公共图库仅本人或管理员，私有空间仅空间创建人）
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        pictureService.checkPictureAuth(loginUser, picture);

        // 2. 功能必须在枚举内且在配置白名单内，避免前端直接驱动未核算的付费功能
        AiImageEditFunctionEnum functionEnum = AiImageEditFunctionEnum.getEnumByValue(aiImageEditCreateRequest.getFunction());
        ThrowUtils.throwIf(functionEnum == null
                        || !AiImageEditFunctionEnum.isAllowed(functionEnum.getValue(), aiImageEditProperties.getAllowedFunctions()),
                ErrorCode.PARAMS_ERROR, "不支持该编辑功能");

        // 3. 提示词：留空则补该功能的通用指令，补完仍为空就不下发
        String prompt = StrUtil.trimToEmpty(aiImageEditCreateRequest.getPrompt());
        if (StrUtil.isBlank(prompt)) {
            prompt = StrUtil.nullToEmpty(functionEnum.getDefaultPrompt());
        }
        // 实测：空字符串提示词会让百炼服务端抛 StringIndexOutOfBoundsException，绝不能发出去
        ThrowUtils.throwIf(StrUtil.isBlank(prompt), ErrorCode.PARAMS_ERROR,
                functionEnum.getText() + "需要填写提示词");
        ThrowUtils.throwIf(prompt.length() > MAX_PROMPT_LENGTH, ErrorCode.PARAMS_ERROR,
                "提示词不能超过 " + MAX_PROMPT_LENGTH + " 个字符");
        // 局部风格化的模板是「把%s变成xx风格」，漏替换会把字面量原样发给模型
        ThrowUtils.throwIf(prompt.contains(PROMPT_PLACEHOLDER), ErrorCode.PARAMS_ERROR,
                "提示词还有未替换的占位符，请指明要编辑的作用对象");

        // 4. 涂抹蒙版：只有局部重绘需要，其余功能传了直接拒绝，避免参数被静默忽略
        String maskImage = resolveMaskImage(aiImageEditCreateRequest.getMaskImage(), functionEnum);

        // 5. 输入图体检：不合规的图在本地就拦下，不消耗额度也不产生费用
        validPictureForAi(picture);

        // 6. 组装功能参数
        Map<String, Object> parameters = buildParameters(functionEnum, aiImageEditCreateRequest);

        // 7. 先占额度再下发，下发失败退还
        String quotaKey = buildQuotaKey(loginUser.getId());
        increaseQuota(quotaKey);
        try {
            AliyunImageEditRequest apiRequest = new AliyunImageEditRequest();
            apiRequest.setFunction(functionEnum.getValue());
            apiRequest.setPrompt(prompt);
            apiRequest.setBaseImageUrl(resolveBaseImage(picture));
            apiRequest.setMaskImageUrl(maskImage);
            apiRequest.setParameters(parameters);
            AiEditCreateTaskResult createResult = aliyunImageEditApi.createTask(apiRequest);
            String errorCode = createResult.getCode();
            if (StrUtil.isNotBlank(errorCode)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, toUserMessage(errorCode, createResult.getMessage()));
            }
            // 8. 记录任务归属，后续轮询与保存都以它为准
            JSONObject record = new JSONObject();
            record.set("userId", loginUser.getId());
            record.set("pictureId", picture.getId());
            record.set("function", functionEnum.getValue());
            stringRedisTemplate.opsForValue().set(TASK_KEY_PREFIX + createResult.getTaskId(), record.toString(),
                    Duration.ofHours(Math.min(aiImageEditProperties.getTaskTtlHours(), ALIYUN_RESULT_KEEP_HOURS)));
            return toTaskVO(createResult.getTaskId(), createResult.getTaskStatus(), null, null);
        } catch (RuntimeException e) {
            refundQuota(quotaKey);
            throw e;
        }
    }

    @Override
    public AiImageEditTaskVO getTask(String taskId, User loginUser) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR, "任务 id 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        JSONObject record = getTaskRecord(taskId, loginUser);

        AiEditQueryTaskResult queryResult = aliyunImageEditApi.queryTask(taskId);
        AiImageEditTaskStatusEnum statusEnum = AiImageEditTaskStatusEnum.getEnumByValue(queryResult.getTaskStatus());
        // 任务本身失败/取消时，错误码属于任务结果而非接口调用失败，要作为失败原因返回给前端
        boolean taskLevelFailure = statusEnum == AiImageEditTaskStatusEnum.FAILED
                || statusEnum == AiImageEditTaskStatusEnum.CANCELED;
        if (StrUtil.isNotBlank(queryResult.getCode()) && !taskLevelFailure) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    toUserMessage(queryResult.getCode(), queryResult.getMessage()));
        }

        String imageUrl = queryResult.getImageUrl();
        String failReason = null;
        if (statusEnum == AiImageEditTaskStatusEnum.SUCCEEDED && StrUtil.isNotBlank(imageUrl)) {
            // 回写结果地址：保存时由服务端自己取，不接受前端传入
            record.set("resultUrl", imageUrl);
            rewriteTaskRecord(taskId, record);
        } else if (taskLevelFailure) {
            failReason = toUserMessage(queryResult.getCode(), queryResult.getMessage());
        } else if (StrUtil.isNotBlank(queryResult.getFailReason())) {
            // 任务成功但个别图片失败
            failReason = queryResult.getFailReason();
        }
        return toTaskVO(taskId, statusEnum.getValue(), imageUrl, failReason);
    }

    @Override
    public PictureVO saveTaskResult(AiImageEditSaveRequest aiImageEditSaveRequest, User loginUser) {
        ThrowUtils.throwIf(aiImageEditSaveRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        String taskId = aiImageEditSaveRequest.getTaskId();
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR, "任务 id 不能为空");
        JSONObject record = getTaskRecord(taskId, loginUser);

        String resultUrl = record.getStr("resultUrl");
        // 前端还没轮询到成功就点保存时，服务端再查一次，不依赖前端回传地址
        if (StrUtil.isBlank(resultUrl)) {
            AiEditQueryTaskResult queryResult = aliyunImageEditApi.queryTask(taskId);
            AiImageEditTaskStatusEnum statusEnum = AiImageEditTaskStatusEnum.getEnumByValue(queryResult.getTaskStatus());
            boolean taskLevelFailure = statusEnum == AiImageEditTaskStatusEnum.FAILED
                    || statusEnum == AiImageEditTaskStatusEnum.CANCELED;
            if (taskLevelFailure || StrUtil.isNotBlank(queryResult.getCode())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        toUserMessage(queryResult.getCode(), queryResult.getMessage()));
            }
            resultUrl = queryResult.getImageUrl();
        }
        ThrowUtils.throwIf(StrUtil.isBlank(resultUrl), ErrorCode.OPERATION_ERROR, "任务尚未完成，请稍后再试");

        Picture oldPicture = pictureService.getById(record.getLong("pictureId"));
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "原图不存在");
        // 与 createTask 对称：结果要落回这张图所属的空间，入库前再核一次权限
        pictureService.checkPictureAuth(loginUser, oldPicture);

        boolean replace = Boolean.TRUE.equals(aiImageEditSaveRequest.getReplace());
        PictureUploadRequest uploadRequest = new PictureUploadRequest();
        // 沿用原图所属空间，保证结果落回同一个库/空间；替换模式下上传链路会校验空间一致性
        uploadRequest.setSpaceId(oldPicture.getSpaceId());
        uploadRequest.setPicName(buildSavePicName(aiImageEditSaveRequest.getPicName(), oldPicture.getName(), replace));
        if (replace) {
            // 走替换分支：数量不变、体积按差值原子调整，原图文件会被异步删除
            uploadRequest.setId(oldPicture.getId());
        }
        // 复用既有上传链路：空间额度校验与原子扣减、压缩/缩略图、审核参数、失败清理都在里面
        PictureVO pictureVO = pictureService.uploadPicture(resultUrl, uploadRequest, loginUser);
        // 结果已入库，百炼临时地址不再需要，删除记录防止重复保存重复扣额度
        stringRedisTemplate.delete(TASK_KEY_PREFIX + taskId);
        log.info("AI 图片编辑结果保存成功, taskId = {}, pictureId = {}, replace = {}, userId = {}",
                taskId, pictureVO.getId(), replace, loginUser.getId());
        return pictureVO;
    }

    @Override
    public List<AiImageEditStyleVO> listStyles(String function) {
        String target = StrUtil.trim(function);
        if (StrUtil.isNotBlank(target)) {
            ThrowUtils.throwIf(AiImageEditFunctionEnum.getEnumByValue(target) == null,
                    ErrorCode.PARAMS_ERROR, "不支持该编辑功能");
        }
        List<AiImageEditStyleVO> voList = new ArrayList<>();
        for (AiImageEditStyleEnum styleEnum : AiImageEditStyleEnum.listByFunction(target)) {
            // 未开放的功能不返回其风格清单，避免前端拿到调不通的选项
            if (!AiImageEditFunctionEnum.isAllowed(styleEnum.getFunction(), aiImageEditProperties.getAllowedFunctions())) {
                continue;
            }
            AiImageEditStyleVO vo = new AiImageEditStyleVO();
            vo.setFunction(styleEnum.getFunction());
            vo.setStyleName(styleEnum.getText());
            vo.setPromptTemplate(styleEnum.getPromptTemplate());
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public AiImageEditQuotaVO getQuota(User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        int limit = aiImageEditProperties.getDailyQuotaPerUser();
        String used = stringRedisTemplate.opsForValue().get(buildQuotaKey(loginUser.getId()));
        long usedCount = StrUtil.isNumeric(used) ? Long.parseLong(used) : 0L;
        AiImageEditQuotaVO vo = new AiImageEditQuotaVO();
        vo.setDailyLimit(limit);
        vo.setRemaining((int) Math.max(0, limit - usedCount));
        return vo;
    }

    @Override
    public AiImageEditBaseImageVO getBaseImage(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片 id 不合法");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        pictureService.checkPictureAuth(loginUser, picture);
        // 与创建任务同样的合规要求：不合规的图本来就进不了 AI 流程
        validPictureForAi(picture);

        // 用库里存的地址取字节：这个 URL 是前端正在成功加载的地址，比反推 COS 对象键可靠
        byte[] bytes;
        try (HttpResponse response = HttpRequest.get(resolveBaseImage(picture))
                .timeout(BASE_IMAGE_TIMEOUT_MILLIS)
                .execute()) {
            if (!response.isOk()) {
                log.error("读取 AI 编辑底图失败, pictureId = {}, status = {}", pictureId, response.getStatus());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取原图失败，请稍后重试");
            }
            bytes = response.bodyBytes();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取 AI 编辑底图异常, pictureId = {}", pictureId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取原图失败，请稍后重试");
        }
        ThrowUtils.throwIf(bytes == null || bytes.length == 0, ErrorCode.OPERATION_ERROR, "原图内容为空");

        String picFormat = StrUtil.trimToEmpty(picture.getPicFormat()).toLowerCase();
        AiImageEditBaseImageVO vo = new AiImageEditBaseImageVO();
        vo.setPictureId(String.valueOf(picture.getId()));
        vo.setDataUrl("data:" + resolveMimeType(picFormat) + ";base64," + Base64.getEncoder().encodeToString(bytes));
        vo.setPicWidth(picture.getPicWidth());
        vo.setPicHeight(picture.getPicHeight());
        vo.setPicFormat(picFormat);
        vo.setPicSize((long) bytes.length);
        return vo;
    }

    /**
     * 图像格式到 MIME 的映射，与接口文档「传值方式」的对照表一致
     */
    private String resolveMimeType(String picFormat) {
        switch (picFormat) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            case "tiff":
                return "image/tiff";
            default:
                return "application/octet-stream";
        }
    }

    // region 校验与参数组装

    /**
     * 输入图体检，规则来自接口约束：格式白名单、宽高 [512,4096]、大小 <= 10MB
     */
    private void validPictureForAi(Picture picture) {
        ThrowUtils.throwIf(StrUtil.isBlank(picture.getUrl()), ErrorCode.PARAMS_ERROR, "图片地址为空，无法进行 AI 编辑");
        String picFormat = StrUtil.trim(picture.getPicFormat());
        ThrowUtils.throwIf(StrUtil.isBlank(picFormat) || !ALLOWED_INPUT_FORMATS.contains(picFormat.toLowerCase()),
                ErrorCode.PARAMS_ERROR, "图片格式不支持，仅支持 jpg / jpeg / png / bmp / tiff / webp");
        Long picSize = picture.getPicSize();
        ThrowUtils.throwIf(picSize != null && picSize > MAX_PIC_SIZE, ErrorCode.PARAMS_ERROR, "图片大小不能超过 10MB");
        Integer picWidth = picture.getPicWidth();
        Integer picHeight = picture.getPicHeight();
        // 宽高可能为 null 或 0（历史数据），这类图无法判断是否合规，直接拦下
        ThrowUtils.throwIf(!isValidSide(picWidth) || !isValidSide(picHeight), ErrorCode.PARAMS_ERROR,
                "图片宽高需在 " + MIN_SIDE_PIXEL + " 到 " + MAX_SIDE_PIXEL + " 像素之间");
    }

    private boolean isValidSide(Integer side) {
        return side != null && side >= MIN_SIDE_PIXEL && side <= MAX_SIDE_PIXEL;
    }

    /**
     * 按功能组装接口 parameters，键名必须是接口要求的 snake_case。
     * n 由 Api 层固定为 1（多图会成倍消耗额度），这里只放水印开关与各功能专属参数。
     */
    private Map<String, Object> buildParameters(AiImageEditFunctionEnum functionEnum, AiImageEditCreateRequest request) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("watermark", aiImageEditProperties.isWatermark());
        switch (functionEnum) {
            case EXPAND: {
                float topScale = validExpandScale(request.getTopScale());
                float bottomScale = validExpandScale(request.getBottomScale());
                float leftScale = validExpandScale(request.getLeftScale());
                float rightScale = validExpandScale(request.getRightScale());
                boolean noExpand = topScale == MIN_EXPAND_SCALE && bottomScale == MIN_EXPAND_SCALE
                        && leftScale == MIN_EXPAND_SCALE && rightScale == MIN_EXPAND_SCALE;
                ThrowUtils.throwIf(noExpand, ErrorCode.PARAMS_ERROR, "请至少设置一个方向的扩展比例");
                parameters.put("top_scale", topScale);
                parameters.put("bottom_scale", bottomScale);
                parameters.put("left_scale", leftScale);
                parameters.put("right_scale", rightScale);
                break;
            }
            case STYLIZATION_ALL:
            case DESCRIPTION_EDIT: {
                parameters.put("strength", validStrength(request.getStrength()));
                break;
            }
            case SUPER_RESOLUTION: {
                parameters.put("upscale_factor", validUpscaleFactor(request.getUpscaleFactor()));
                break;
            }
            case DOODLE: {
                parameters.put("is_sketch", Boolean.TRUE.equals(request.getIsSketch()));
                break;
            }
            default: {
                // 其余功能只使用通用参数
                break;
            }
        }
        return parameters;
    }

    private float validExpandScale(Float scale) {
        if (scale == null) {
            return MIN_EXPAND_SCALE;
        }
        ThrowUtils.throwIf(scale < MIN_EXPAND_SCALE || scale > MAX_EXPAND_SCALE, ErrorCode.PARAMS_ERROR,
                "扩展比例需在 " + MIN_EXPAND_SCALE + " 到 " + MAX_EXPAND_SCALE + " 之间");
        return scale;
    }

    private float validStrength(Float strength) {
        if (strength == null) {
            return DEFAULT_STRENGTH;
        }
        ThrowUtils.throwIf(strength < MIN_STRENGTH || strength > MAX_STRENGTH, ErrorCode.PARAMS_ERROR,
                "修改幅度需在 0.0 到 1.0 之间");
        return strength;
    }

    private int validUpscaleFactor(Integer upscaleFactor) {
        if (upscaleFactor == null) {
            return MIN_UPSCALE_FACTOR;
        }
        ThrowUtils.throwIf(upscaleFactor < MIN_UPSCALE_FACTOR || upscaleFactor > MAX_UPSCALE_FACTOR,
                ErrorCode.PARAMS_ERROR, "超分放大倍数需在 1 到 4 之间");
        return upscaleFactor;
    }

    /**
     * 涂抹蒙版只接受前端生成的黑白图像 base64（不落 COS，避免污染图库并占用空间额度）。
     * 接口要求蒙版分辨率与原图一致，这一点由前端保证，后端不做解码校验。
     */
    private String resolveMaskImage(String maskImage, AiImageEditFunctionEnum functionEnum) {
        String value = StrUtil.trim(maskImage);
        if (functionEnum.isNeedMask()) {
            ThrowUtils.throwIf(StrUtil.isBlank(value), ErrorCode.PARAMS_ERROR, "局部重绘需要提供涂抹区域图像");
        } else {
            ThrowUtils.throwIf(StrUtil.isNotBlank(value), ErrorCode.PARAMS_ERROR, "当前功能不需要涂抹区域图像");
        }
        if (StrUtil.isBlank(value)) {
            return null;
        }
        ThrowUtils.throwIf(value.length() > MAX_MASK_BASE64_LENGTH, ErrorCode.PARAMS_ERROR,
                "涂抹区域图像过大，请降低分辨率后重试");
        // 裸 base64 补上前缀，接口要求格式为 data:{MIME_type};base64,{base64_data}
        return value.startsWith("data:") ? value : "data:image/png;base64," + value;
    }

    /**
     * 取输入图地址，原样使用库里的 url（前端能正常显示，说明该地址本身就是可访问的对象地址，
     * 其中的重复斜杠可能属于 COS 对象键本身，规范化反而会 404，因此这里不做任何改写）。
     * 当前 COS 桶公开可读，直接传 URL；将来若启用 CDN 防盗链，只需在此改为「COS 私有读 + base64 data URI」，其余逻辑不用动。
     */
    private String resolveBaseImage(Picture picture) {
        return picture.getUrl();
    }

    private String buildSavePicName(String requestName, String oldName, boolean replace) {
        String customName = StrUtil.trim(requestName);
        if (StrUtil.isNotBlank(customName)) {
            return customName;
        }
        // 替换原图时保留原名，避免反复编辑把名字后缀越加越长
        if (replace) {
            return StrUtil.blankToDefault(oldName, "AI编辑图片");
        }
        return StrUtil.blankToDefault(oldName, "AI编辑图片") + "_AI编辑";
    }

    // endregion

    // region Redis 任务记录与日额度

    /**
     * 读取任务记录并校验归属：任务 id 虽然是随机 uuid，但越权拿别人的生成结果仍然不该被允许
     */
    private JSONObject getTaskRecord(String taskId, User loginUser) {
        String json = stringRedisTemplate.opsForValue().get(TASK_KEY_PREFIX + taskId);
        ThrowUtils.throwIf(StrUtil.isBlank(json), ErrorCode.NOT_FOUND_ERROR, "任务不存在或已过期");
        JSONObject record = JSONUtil.parseObj(json);
        Long ownerId = record.getLong("userId");
        ThrowUtils.throwIf(ownerId == null || !ownerId.equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权操作该任务");
        return record;
    }

    /**
     * 回写记录时沿用原有剩余过期时间，避免每次轮询都把有效期续成 23 小时
     */
    private void rewriteTaskRecord(String taskId, JSONObject record) {
        String key = TASK_KEY_PREFIX + taskId;
        Long remainSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        Duration ttl = (remainSeconds != null && remainSeconds > 0)
                ? Duration.ofSeconds(remainSeconds)
                : Duration.ofHours(Math.min(aiImageEditProperties.getTaskTtlHours(), ALIYUN_RESULT_KEEP_HOURS));
        stringRedisTemplate.opsForValue().set(key, record.toString(), ttl);
    }

    private String buildQuotaKey(Long userId) {
        return QUOTA_KEY_PREFIX + userId + ":" + DateUtil.format(new Date(), "yyyyMMdd");
    }

    /**
     * 占用一次日额度。按提交次数计，任务执行失败不退还（它是防刷护栏不是计费账本，百炼侧失败本身不产生费用）。
     * 每次自增后都重设过期时间：INCR 与 EXPIRE 是两条命令，若上次中途断掉留下无 TTL 的键，这样能自愈，
     * 也保证超额被拒时不会把计数白白推高。
     */
    private void increaseQuota(String quotaKey) {
        Long count = stringRedisTemplate.opsForValue().increment(quotaKey);
        stringRedisTemplate.expire(quotaKey, Duration.ofHours(25));
        int limit = aiImageEditProperties.getDailyQuotaPerUser();
        if (count != null && count > limit) {
            stringRedisTemplate.opsForValue().increment(quotaKey, -1);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "今日 AI 编辑次数已用完，请明天再试");
        }
    }

    private void refundQuota(String quotaKey) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(quotaKey, -1);
            if (count != null && count < 0) {
                stringRedisTemplate.delete(quotaKey);
            }
        } catch (Exception e) {
            log.error("回滚 AI 编辑日额度失败, key = {}", quotaKey, e);
        }
    }

    // endregion

    /**
     * 把百炼错误码翻译成用户能看懂的文案；配置类错误只记日志，不把细节暴露给前端
     */
    private String toUserMessage(String code, String message) {
        if (StrUtil.isBlank(code)) {
            return "AI 服务调用失败，请稍后重试";
        }
        log.error("AI 图片编辑接口返回错误, code = {}, message = {}", code, message);
        switch (code) {
            case "InvalidParameter":
                // 这个错误码覆盖参数/图片/提示词等多种情况，直接把百炼原文带出来才排得动。
                // 对外上线前应改回笼统文案，避免暴露第三方服务的内部错误信息。
                return "请求参数不符合要求：" + StrUtil.blankToDefault(message, "请检查图片与参数");
            case "DataInspectionFailed":
                return "图片或提示词可能包含敏感内容，请修改后重试";
            case "IPInfringementSuspect":
                return "输入内容涉嫌侵权，请更换图片";
            case "InternalError":
                return "AI 服务异常，请稍后重试";
            case "InvalidApiKey":
            case "AccessDenied":
            case "Unauthorized":
                return "AI 服务暂不可用，请联系管理员";
            default:
                if (code.startsWith("Throttling")) {
                    return "AI 服务繁忙，请稍后重试";
                }
                if (code.startsWith("HTTP_") || "EmptyTaskId".equals(code) || "EmptyOutput".equals(code)) {
                    return "AI 服务暂时不可用，请稍后重试";
                }
                return "AI 服务调用失败，请稍后重试";
        }
    }

    private AiImageEditTaskVO toTaskVO(String taskId, String taskStatus, String imageUrl, String failReason) {
        AiImageEditTaskStatusEnum statusEnum = AiImageEditTaskStatusEnum.getEnumByValue(taskStatus);
        AiImageEditTaskVO vo = new AiImageEditTaskVO();
        vo.setTaskId(taskId);
        vo.setTaskStatus(statusEnum.getValue());
        vo.setStatusText(statusEnum.getText());
        vo.setImageUrl(imageUrl);
        vo.setFailReason(failReason);
        return vo;
    }
}
