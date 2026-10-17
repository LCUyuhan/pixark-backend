package com.yeyuchunlan.pixarkbackend.controller;


import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yeyuchunlan.pixarkbackend.annotation.AuthCheck;
import com.yeyuchunlan.pixarkbackend.common.BaseResponse;
import com.yeyuchunlan.pixarkbackend.common.ResultUtils;
import com.yeyuchunlan.pixarkbackend.constant.UserConstant;
import com.yeyuchunlan.pixarkbackend.exception.BusinessException;
import com.yeyuchunlan.pixarkbackend.exception.ErrorCode;
import com.yeyuchunlan.pixarkbackend.exception.ThrowUtils;
import com.yeyuchunlan.pixarkbackend.model.dto.space.analyze.*;
import com.yeyuchunlan.pixarkbackend.model.entity.Picture;
import com.yeyuchunlan.pixarkbackend.model.entity.Space;
import com.yeyuchunlan.pixarkbackend.model.entity.User;
import com.yeyuchunlan.pixarkbackend.model.vo.space.analyze.*;
import com.yeyuchunlan.pixarkbackend.service.PictureService;
import com.yeyuchunlan.pixarkbackend.service.SpaceAnalyzeService;
import com.yeyuchunlan.pixarkbackend.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 空间分析接口，提供空间资源使用情况与图片分类维度的统计分析能力
 */
@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {

    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;

    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;


    /**
     * 获取空间使用状态
     *
     * @param spaceUsageAnalyzeRequest SpaceUsageAnalyzeRequest 请求参数
     * @param request                  HttpServletRequest 请求对象
     * @return BaseResponse<SpaceUsageAnalyzeResponse> 分析结果
     */
    @PostMapping("/usage")
    public BaseResponse<SpaceUsageAnalyzeResponse> getSpaceUsageAnalyze(@RequestBody SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, HttpServletRequest request) {
        // 校验请求参数
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户，服务端会据此校验空间使用分析的访问权限
        User loginUser = userService.getLoginUser(request);
        // 统计空间的已用容量、已用图片数及对应使用占比
        SpaceUsageAnalyzeResponse spaceUsageAnalyze = spaceAnalyzeService.getSpaceUsageAnalyze(spaceUsageAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceUsageAnalyze);
    }


    /**
     * 获取空间图片分类情况
     *
     * @param spaceCategoryAnalyzeRequest SpaceCategoryAnalyzeRequest 请求参数
     * @param request                     HttpServletRequest 请求对象
     * @return BaseResponse<List<SpaceCategoryAnalyzeResponse>> 分析结果
     */
    @PostMapping("/category")
    public BaseResponse<List<SpaceCategoryAnalyzeResponse>> getSpaceCategoryAnalyze(@RequestBody SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, HttpServletRequest request) {
        // 校验请求参数
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户，服务端会据此校验空间分析权限
        User loginUser = userService.getLoginUser(request);
        // 按分类维度统计空间图片数据
        List<SpaceCategoryAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceCategoryAnalyze(spaceCategoryAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 获取空间图片标签情况
     *
     * @param spaceTagAnalyzeRequest SpaceTagAnalyzeRequest 请求参数
     * @param request                HttpServletRequest 请求对象
     * @return BaseResponse<List<SpaceTagAnalyzeResponse>> 分析结果
     */
    @PostMapping("/tag")
    public BaseResponse<List<SpaceTagAnalyzeResponse>> getSpaceTagAnalyze(@RequestBody SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, HttpServletRequest request) {
        // 校验请求参数
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户，服务端会据此校验空间分析权限
        User loginUser = userService.getLoginUser(request);
        // 按标签维度统计空间图片数据
        List<SpaceTagAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceTagAnalyze(spaceTagAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 获取空间图片大小情况
     *
     * @param spaceSizeAnalyzeRequest SpaceSizeAnalyzeRequest 请求参数
     * @param request                 HttpServletRequest 请求对象
     * @return BaseResponse<List<SpaceSizeAnalyzeResponse>> 分析结果
     */
    @PostMapping("/size")
    public BaseResponse<List<SpaceSizeAnalyzeResponse>> getSpaceSizeAnalyze(@RequestBody SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, HttpServletRequest request) {
        // 校验请求参数
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户，服务端会据此校验空间分析权限
        User loginUser = userService.getLoginUser(request);
        // 按图片大小分段统计空间图片数据
        List<SpaceSizeAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceSizeAnalyze(spaceSizeAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }

    /**
     * 获取空间用户上传图片情况
     *
     * @param spaceUserAnalyzeRequest SpaceUserAnalyzeRequest 请求参数
     * @param request                 HttpServletRequest 请求对象
     * @return BaseResponse<List<SpaceUserAnalyzeResponse>> 分析结果
     */
    @PostMapping("/user")
    public BaseResponse<List<SpaceUserAnalyzeResponse>> getSpaceUserAnalyze(@RequestBody SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, HttpServletRequest request) {
        // 校验请求参数
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户，服务端会据此校验空间分析权限
        User loginUser = userService.getLoginUser(request);
        // 按时间维度（每日/每周/每月）统计空间用户上传图片数量
        List<SpaceUserAnalyzeResponse> resultList = spaceAnalyzeService.getSpaceUserAnalyze(spaceUserAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }


    /**
     * 获取空间排名情况(仅管理员可用)
     *
     * @param spaceRankAnalyzeRequest SpaceRankAnalyzeRequest 请求参数
     * @param request                 HttpServletRequest 请求对象
     * @return BaseResponse<List<Space>> 分析结果
     */
    @PostMapping("/rank")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<Space>> getSpaceRankAnalyze(@RequestBody SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, HttpServletRequest request) {
        // 校验请求参数
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);

        // 获取当前登录用户，服务端会据此校验空间排行的访问权限（仅管理员可用）
        User loginUser = userService.getLoginUser(request);
        // 按空间总容量降序排行，取前 topN 个空间
        List<Space> resultList = spaceAnalyzeService.getSpaceRankAnalyze(spaceRankAnalyzeRequest, loginUser);
        return ResultUtils.success(resultList);
    }



}