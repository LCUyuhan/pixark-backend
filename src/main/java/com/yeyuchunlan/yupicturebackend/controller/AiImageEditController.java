package com.yeyuchunlan.yupicturebackend.controller;

import cn.hutool.core.util.StrUtil;
import com.yeyuchunlan.yupicturebackend.common.BaseResponse;
import com.yeyuchunlan.yupicturebackend.common.ResultUtils;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.exception.ThrowUtils;
import com.yeyuchunlan.yupicturebackend.manager.PictureCacheManager;
import com.yeyuchunlan.yupicturebackend.model.dto.aiimageedit.AiImageEditCreateRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.aiimageedit.AiImageEditSaveRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.vo.picture.AiImageEditBaseImageVO;
import com.yeyuchunlan.yupicturebackend.model.vo.picture.AiImageEditQuotaVO;
import com.yeyuchunlan.yupicturebackend.model.vo.picture.AiImageEditStyleVO;
import com.yeyuchunlan.yupicturebackend.model.vo.picture.AiImageEditTaskVO;
import com.yeyuchunlan.yupicturebackend.model.vo.picture.PictureVO;
import com.yeyuchunlan.yupicturebackend.service.AiImageEditService;
import com.yeyuchunlan.yupicturebackend.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * AI 图片编辑接口
 * 与 PictureController 分开：AI 编辑是独立子领域（异步任务、额度扣减/退还、外部依赖），
 * 变更频率与失败模式都不同于图片 CRUD。根路径保持 /picture/ai/edit，接口 URL 与拆分前一致。
 */
@RestController
@RequestMapping("/picture/ai/edit")
public class AiImageEditController {

    @Resource
    private AiImageEditService aiImageEditService;

    @Resource
    private UserService userService;

    @Resource
    private PictureCacheManager pictureCacheManager;

    /**
     * 创建 AI 图片编辑任务（接口只支持异步，需轮询查询接口获取结果）。
     * 受两道闸限制：每分钟提交次数（ai-image-edit.submit-rate-per-minute）与 user.aiQuota 余额，
     * 任务下发失败或执行失败都会把余额退回去
     */
    @PostMapping("/create_task")
    public BaseResponse<AiImageEditTaskVO> createAiImageEditTask(@RequestBody AiImageEditCreateRequest aiImageEditCreateRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(aiImageEditCreateRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        AiImageEditTaskVO taskVO = aiImageEditService.createTask(aiImageEditCreateRequest, loginUser);
        return ResultUtils.success(taskVO);
    }

    /**
     * 查询 AI 图片编辑任务，仅任务提交人可查；返回的图片地址 24 小时后失效
     */
    @GetMapping("/get_task")
    public BaseResponse<AiImageEditTaskVO> getAiImageEditTask(String taskId, HttpServletRequest request) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR, "任务 id 不能为空");
        User loginUser = userService.getLoginUser(request);
        AiImageEditTaskVO taskVO = aiImageEditService.getTask(taskId, loginUser);
        return ResultUtils.success(taskVO);
    }

    /**
     * 保存 AI 图片编辑结果，replace 为 true 时覆盖原图（原图文件会被删除）
     */
    @PostMapping("/save")
    public BaseResponse<PictureVO> saveAiImageEditResult(@RequestBody AiImageEditSaveRequest aiImageEditSaveRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(aiImageEditSaveRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = aiImageEditService.saveTaskResult(aiImageEditSaveRequest, loginUser);
        // 管理员编辑公共图并直接过审入库时，不清缓存新图最多 10 分钟不出现在首页
        if (pictureVO.getSpaceId() == null && userService.isAdmin(loginUser)) {
            pictureCacheManager.clearPublicGalleryCache();
        }
        return ResultUtils.success(pictureVO);
    }

    /**
     * 查询风格化功能的预设风格清单（仅返回已开放的功能）
     */
    @GetMapping("/styles")
    public BaseResponse<List<AiImageEditStyleVO>> listAiImageEditStyles(String function, HttpServletRequest request) {
        userService.getLoginUser(request);
        List<AiImageEditStyleVO> styleList = aiImageEditService.listStyles(function);
        return ResultUtils.success(styleList);
    }

    /**
     * 查询当前用户的 AI 编辑额度余量（打开 AI 编辑弹窗时调用）
     */
    @GetMapping("/quota")
    public BaseResponse<AiImageEditQuotaVO> getAiImageEditQuota(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        AiImageEditQuotaVO quotaVO = aiImageEditService.getQuota(loginUser);
        return ResultUtils.success(quotaVO);
    }

    /**
     * 取原图的 base64 底图，供前端本地画布绘制涂抹蒙版（只读，不消耗额度）
     */
    @GetMapping("/base_image")
    public BaseResponse<AiImageEditBaseImageVO> getAiImageEditBaseImage(Long pictureId, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片 id 不合法");
        User loginUser = userService.getLoginUser(request);
        AiImageEditBaseImageVO baseImageVO = aiImageEditService.getBaseImage(pictureId, loginUser);
        return ResultUtils.success(baseImageVO);
    }
}
