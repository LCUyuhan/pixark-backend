package com.yeyuchunlan.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureReviewRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author him
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-09-09 16:17:43
*/
public interface PictureService extends IService<Picture> {


    /**
     * 上传图片
     *
     * @param inputSource  文件
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser 登录用户
     * @return 上传图片结果
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 将查询请求封装成QueryWrapper查询条件
     *
     * @param pictureQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);


    /**
     * 获取单条封装类
     *
     * @param picture       图片
     * @param request       请求
     * @return 封装类
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 分页获取图片封装对象
     * <p>
     * 将数据库查询到的 Picture 实体分页数据，转换为 PictureVO 分页数据，
     * 同时为每张图片关联查询并填充对应的用户信息。
     *
     * @param picturePage 图片实体分页数据（从数据库查询到的原始分页结果）
     * @param request     HTTP 请求对象（可用于获取当前登录用户等信息）
     * @return 图片封装对象的分页结果（包含用户信息的 VO 分页数据）
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 校验图片
     *
     * @param picture 图片
     */
    void validPicture(Picture picture);


    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser 登录用户
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核参数
     *
     * @param picture 图片
     * @param loginUser 登录用户
     */
    void fillReviewParams(Picture picture, User loginUser);


    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 图片批量上传请求
     * @param loginUser 登录用户
     * @return 成功创建的图片数
     */
    int uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

}
