package com.yeyuchunlan.pixarkbackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeyuchunlan.pixarkbackend.annotation.AuthCheck;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.ImageSearchSelector;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.ImageSearchService;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.model.ImageSearchResult;
import com.yeyuchunlan.pixarkbackend.common.BaseResponse;
import com.yeyuchunlan.pixarkbackend.common.DeleteRequest;
import com.yeyuchunlan.pixarkbackend.common.ResultUtils;
import com.yeyuchunlan.pixarkbackend.config.GuestPictureProperties;
import com.yeyuchunlan.pixarkbackend.constant.UserConstant;
import com.yeyuchunlan.pixarkbackend.exception.BusinessException;
import com.yeyuchunlan.pixarkbackend.exception.ErrorCode;
import com.yeyuchunlan.pixarkbackend.exception.ThrowUtils;
import com.yeyuchunlan.pixarkbackend.manager.PictureCacheManager;
import com.yeyuchunlan.pixarkbackend.model.dto.picture.*;
import com.yeyuchunlan.pixarkbackend.model.entity.Picture;
import com.yeyuchunlan.pixarkbackend.model.entity.Space;
import com.yeyuchunlan.pixarkbackend.model.entity.User;
import com.yeyuchunlan.pixarkbackend.model.enums.PictureReviewStatusEnum;
import com.yeyuchunlan.pixarkbackend.model.vo.picture.PictureTagCategory;
import com.yeyuchunlan.pixarkbackend.model.vo.picture.PictureVO;
import com.yeyuchunlan.pixarkbackend.service.PictureService;
import com.yeyuchunlan.pixarkbackend.service.SpaceService;
import com.yeyuchunlan.pixarkbackend.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private ImageSearchSelector imageSearchSelector;
    @Resource
    private PictureCacheManager pictureCacheManager;
    @Resource
    private GuestPictureProperties guestPictureProperties;





    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")

    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        // 管理员上传公共图会直接过审（fillReviewParams），不清缓存的话新图最多 10 分钟不出现在首页
        if (pictureVO.getSpaceId() == null && userService.isAdmin(loginUser)) {
            pictureCacheManager.clearPublicGalleryCache();
        }
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        if (pictureVO.getSpaceId() == null && userService.isAdmin(loginUser)) {
            pictureCacheManager.clearPublicGalleryCache();
        }
        return ResultUtils.success(pictureVO);
    }


    /**
     * 删除图片
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 调用服务层方法删除图片
        Picture deletedPicture = pictureService.deletePicture(id, loginUser);
        // 列表缓存只覆盖公共图库的图，删私有空间的图不该把它打回冷启动
        if (deletedPicture.getSpaceId() == null) {
            pictureCacheManager.clearPublicGalleryCache();
        }
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            User loginUser = userService.getLoginUser(request);
            pictureService.checkPictureAuth(loginUser, picture);
        }
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        pictureQueryRequest.normalizePage();
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     * 公共图库（spaceId 为 null）走缓存，私有空间直接查库
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        pictureQueryRequest.normalizePage();
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库：普通用户默认只能查看已过审的公开数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间：不缓存，直接查库
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
            // 私有空间直接查库，不走缓存
            Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                    pictureService.getQueryWrapper(pictureQueryRequest));
            return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
        }
        // === 以下为公共图库缓存逻辑（本地 -> Redis -> DB 三级，交由 PictureCacheManager 编排）===
        String cacheKey = pictureCacheManager.buildCacheKey(pictureQueryRequest);
        Page<PictureVO> cachedPage = pictureCacheManager.getCachePage(cacheKey);
        if (cachedPage != null) {
            return ResultUtils.success(limitForGuest(cachedPage, current, size, request));
        }
        // 缓存未命中：查询数据库并回写缓存
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        pictureCacheManager.putCachePage(cacheKey, pictureVOPage);
        return ResultUtils.success(limitForGuest(pictureVOPage, current, size, request));
    }

    /**
     * 游客只能浏览公共图库的前 N 张（N 配在 guest-picture.view-limit）。
     * 整页照常查询、照常进缓存，只在返回前裁 records，因此游客与登录用户共用同一条缓存；
     * 两个调用方拿到的都是新实例（缓存走反序列化、回源走新建），原地裁不会污染缓存。
     */
    private Page<PictureVO> limitForGuest(Page<PictureVO> page, long current, long size, HttpServletRequest request) {
        if (request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE) != null) {
            return page;
        }
        List<PictureVO> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return page;
        }
        int allow = (int) Math.max(0, guestPictureProperties.getViewLimit() - (current - 1) * size);
        if (allow < records.size()) {
            page.setRecords(new ArrayList<>(records.subList(0, allow)));
        }
        return page;
    }

    /**
     * 我上传的公共图片分页（仅本人，含待审核与被拒绝，供普通用户跟审核进度）
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<PictureVO>> listMyPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                               HttpServletRequest request) {
        pictureQueryRequest.normalizePage();
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 归属以登录态为准，前端传什么 userId 都无效；只列公共图库，空间图在空间页看
        pictureQueryRequest.setUserId(loginUser.getId());
        pictureQueryRequest.setNullSpaceId(true);
        // 个人数据不接公共图库缓存：量小、且审核状态变化频繁
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 编辑图片（给用户和管理员使用）
     *
     * @param pictureEditRequest 图片编辑请求
     *
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        // 调用服务层方法编辑图片
        Picture oldPicture = pictureService.editPicture(pictureEditRequest, loginUser);
        // 只有公共图库的图会被列表缓存覆盖；编辑后它要么文案变了，要么被打回待审需要从列表摘掉
        if (oldPicture.getSpaceId() == null) {
            pictureCacheManager.clearPublicGalleryCache();
        }
        return ResultUtils.success(true);
    }

    /**
     * 获取图片标签和分类（封装类）
     *
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        //目前写死
        //todo动态获取热门标签和分类
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("风景", "游戏", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 图片审核（仅管理员可用）
     *
     * @param pictureReviewRequest 图片审核请求
     * @return 响应
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        // 清除公共图库缓存，确保审核结果及时展示
        pictureCacheManager.clearPublicGalleryCache();
        return ResultUtils.success(true);
    }

     /**
     * 上传图片（批量）（仅管理员可用）
     *
     * @param pictureUploadByBatchRequest 图片上传请求
     * @return 响应
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        // 清除公共图库缓存
        pictureCacheManager.clearPublicGalleryCache();
        return ResultUtils.success(uploadCount);
    }

    /**
     * 以图搜图（从全网搜索图片）
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest,
                                                                        HttpServletRequest request) {
        // 登录校验：任何已登录用户可调用（未登录 getLoginUser 抛 NOT_LOGIN）
        userService.getLoginUser(request);
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        ImageSearchService imageSearchService = imageSearchSelector.pick(searchPictureByPictureRequest.getProvider());
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        String searchUrl = (thumbnailUrl != null && !thumbnailUrl.trim().isEmpty())
                ? thumbnailUrl : oldPicture.getUrl();
        List<ImageSearchResult> resultList = imageSearchService.search(searchUrl);
        return ResultUtils.success(resultList);
    }

    /**
     * 颜色搜索（从个人空间搜索图片）
     */
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> result = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 批量编辑图片（在个人空间中使用）
     *
     * @param pictureEditByBatchRequest 图片批量编辑请求
     * @return 响应
     */
    @PostMapping("/edit/batch")
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

}
