package com.yeyuchunlan.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yeyuchunlan.yupicturebackend.config.CosClientConfig;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.exception.ThrowUtils;
import com.yeyuchunlan.yupicturebackend.manager.CosManager;
import com.yeyuchunlan.yupicturebackend.manager.FileManager;
import com.yeyuchunlan.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yeyuchunlan.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureReviewRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.Picture;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yeyuchunlan.yupicturebackend.model.vo.PictureVO;
import com.yeyuchunlan.yupicturebackend.model.vo.UserVO;
import com.yeyuchunlan.yupicturebackend.service.PictureService;
import com.yeyuchunlan.yupicturebackend.mapper.PictureMapper;
import com.yeyuchunlan.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author him
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-09-09 16:17:43
*/
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    private FileManager fileManager;
    @Resource
    private UserService userService;
    @Resource
    private PictureUploadTemplate filePictureUpload;
    @Resource
    private PictureUploadTemplate urlPictureUpload;
    @Resource
    private CosManager cosManager;
    @Resource
    private CosClientConfig cosClientConfig;
    // 注入自身代理，保证类内部调用 clearPictureFile 时 @Async 依然生效
    @Autowired
    @Lazy
    private PictureService self;

    /**
     * 允许排序的字段白名单（请求参数值 -> 数据库列名），防止 sortField 直接拼入 SQL
     */
    private static final Map<String, String> ALLOW_SORT_FIELD_MAP = Map.of(
            "createTime", "createTime",
            "editTime", "editTime",
            "updateTime", "updateTime",
            "id", "id",
            "name", "name",
            "picSize", "picSize"
    );


    /**
     * 上传图片
     *
     * @param inputSource 输入源
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser 登录用户
     * @return 上传图片结果
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        if (inputSource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片为空");
        }
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 用于判断是新增还是更新图片
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新图片，需要校验图片是否存在
        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
        // 上传图片，得到信息
        // 按照用户 id 划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        // 根据 inputSource 类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        //调用 FileManager 将用户上传的图片文件上传到腾讯云 COS 对象存储，并返回图片的详细信息。
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        String picName = uploadPictureResult.getPicName();
        // 如果用户指定了图片名称，则使用用户指定的图片名称
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        //补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
        // 重新上传时，清理被替换掉的旧图片文件（通过自身代理调用，保证 @Async 生效）
        if (oldPicture != null) {
            self.clearPictureFile(oldPicture);
        }
        return PictureVO.objToVo(picture);
    }


    /**
     * 将查询请求封装成QueryWrapper查询条件
     *
     * @param pictureQueryRequest 查询请求
     * @return 查询条件
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);

        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序：字段经白名单校验，非法字段忽略；常量在前调用 equals 避免 sortOrder 为 null 时 NPE
        String sortColumn = ALLOW_SORT_FIELD_MAP.get(sortField);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortColumn), "ascend".equals(sortOrder), sortColumn);
        return queryWrapper;
    }


    /**
     * 获取单条封装类
     *
     * @param picture       图片
     * @param request       请求
     * @return 封装类
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }


    /**
     * 分页获取图片封装对象
     *
     * 将数据库查询到的 Picture 实体分页数据，转换为 PictureVO 分页数据，
     * 同时为每张图片关联查询并填充对应的用户信息。
     *
     *
     * @param picturePage 图片实体分页数据（从数据库查询到的原始分页结果）
     * @param request     HTTP 请求对象（可用于获取当前登录用户等信息）
     * @return 图片封装对象的分页结果（包含用户信息的 VO 分页数据）
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        // 从分页对象中获取当前页的图片实体列表
        List<Picture> pictureList = picturePage.getRecords();
        // 创建一个新的 PictureVO 分页对象，保留原分页的页码、每页大小、总记录数
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        // 如果当前页没有数据，直接返回空的分页对象，避免后续无意义的处理
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 将 Picture 实体列表转换为 PictureVO 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息：收集所有用户 ID，批量查询后按 ID 分组
        // 提取所有图片的 userId，放入 Set 中去重，避免重复查询同一个用户
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 根据去重后的 userId 集合批量查询用户，并按用户 ID 分组，方便后续快速查找
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息：为每个图片 VO 设置对应的用户信息
        // 遍历每个图片 VO，根据 userId 从分组好的 Map 中查找对应用户，并设置到 VO 中
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            // 如果该 userId 在用户 Map 中存在，则取出对应的用户对象
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            // 将 User 实体转换为 UserVO，并设置到图片 VO 中
            pictureVO.setUser(userService.getUserVO(user));
        });
        // 将填充好用户信息的 VO 列表设置到分页对象中
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }



    /**
     * 校验图片
     *
     * @param picture 图片
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR, "图片不能为空");
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            // 与 introduction varchar(512) 列宽对齐
            ThrowUtils.throwIf(introduction.length() > 512, ErrorCode.PARAMS_ERROR, "简介过长");
        }
        // tags 列为 varchar(512)，补充长度校验
        String tags = picture.getTags();
        if (StrUtil.isNotBlank(tags)) {
            ThrowUtils.throwIf(tags.length() > 512, ErrorCode.PARAMS_ERROR, "标签过长");
        }
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser 登录用户
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 填充审核参数
     *
     * @param picture 图片
     * @param loginUser 登录用户
     **/
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 图片批量上传请求
     * @param loginUser 登录用户
     * @return 成功创建的图片数
     */
    @Override
    public int uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        // 格式化数量（count 为 null 时默认 10，并校验取值范围，避免拆箱 NPE）
        Integer count = ObjUtil.defaultIfNull(pictureUploadByBatchRequest.getCount(), 10);
        ThrowUtils.throwIf(count < 1 || count > 30, ErrorCode.PARAMS_ERROR, "数量需在 1~30 之间");
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        //Elements imgElementList = div.select("img.mimg");
        Elements imgElementList = div.select(".iusc");  // 修改选择器，获取包含完整数据的元素
        // 将 Elements 转为 List 并打乱顺序，避免每次抓取结果相同
        List<Element> imgList = new java.util.ArrayList<>(imgElementList);
        Collections.shuffle(imgList);
        int uploadCount = 0;
        for (Element imgElement : imgList) {
            //String fileUrl = imgElement.attr("src");
            // 获取data-m属性中的JSON字符串
            String dataM = imgElement.attr("m");
            String fileUrl;
            try {
                // 解析JSON字符串
                JSONObject jsonObject = JSONUtil.parseObj(dataM);
                // 获取murl字段（原始图片URL）
                fileUrl = jsonObject.getStr("murl");
            } catch (Exception e) {
                log.error("解析图片数据失败", e);
                continue;
            }

            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
            if (StrUtil.isBlank(namePrefix)) {
                namePrefix = searchText;
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            if (StrUtil.isNotBlank(namePrefix)) {
                // 设置图片名称，序号连续递增
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    /**
     * 异步清理图片文件
     *
     * @param oldPicture 旧图片
     */
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判空，避免 url 为空时 substring 越界
        String pictureUrl = oldPicture.getUrl();
        if (StrUtil.isBlank(pictureUrl)) {
            return;
        }
        // 判断该图片是否还被其他记录使用（此处按“记录已删除后调用”的逻辑，count > 0 即表示仍被引用）
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        if (count > 0) {
            return;
        }
        try {
            // url 形如 host + "/" + key，删除对象需要的是纯 key（存储路径）
            String key = pictureUrl.substring(cosClientConfig.getHost().length() + 1);
            cosManager.deleteObject(key);
            // 清理缩略图
            String thumbnailUrl = oldPicture.getThumbnailUrl();
            if (StrUtil.isNotBlank(thumbnailUrl)) {
                String thumbnailKey = thumbnailUrl.substring(cosClientConfig.getHost().length() + 1);
                cosManager.deleteObject(thumbnailKey);
            }
        } catch (Exception e) {
            // 异步线程中的异常不会影响主流程，但必须记录，避免静默失败
            log.error("清理图片文件失败, pictureUrl = {}", pictureUrl, e);
        }
    }


}




