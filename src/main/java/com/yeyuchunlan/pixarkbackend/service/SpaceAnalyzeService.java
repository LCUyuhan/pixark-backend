package com.yeyuchunlan.pixarkbackend.service;


import com.yeyuchunlan.pixarkbackend.model.dto.space.analyze.*;
import com.yeyuchunlan.pixarkbackend.model.entity.Space;
import com.yeyuchunlan.pixarkbackend.model.entity.User;
import com.yeyuchunlan.pixarkbackend.model.vo.space.analyze.*;

import java.util.List;

public interface SpaceAnalyzeService {

    /**
     * 获取空间使用情况分析
     *
     * @param spaceUsageAnalyzeRequest SpaceUsageAnalyzeRequest 请求参数
     * @param loginUser                当前登录用户
     * @return SpaceUsageAnalyzeResponse 分析结果
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    /**
     * 获取空间图片分类情况分析
     *
     * @param spaceCategoryAnalyzeRequest SpaceCategoryAnalyzeRequest 请求参数
     * @param loginUser                   当前登录用户
     * @return List<SpaceCategoryAnalyzeResponse> 分析结果
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser);

    /**
     * 获取空间图片标签情况分析
     *
     * @param spaceTagAnalyzeRequest SpaceTagAnalyzeRequest 请求参数
     * @param loginUser              当前登录用户
     * @return List<SpaceTagAnalyzeResponse> 分析结果
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 获取空间图片大小情况分析
     *
     * @param spaceSizeAnalyzeRequest SpaceSizeAnalyzeRequest 请求参数
     * @param loginUser               当前登录用户
     * @return List<SpaceSizeAnalyzeResponse> 分析结果
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);


    /**
     * 获取空间用户上传图片分析
     *
     * @param spaceUserAnalyzeRequest SpaceUserAnalyzeRequest 请求参数
     * @param loginUser               User 登录用户
     * @return List<SpaceUserAnalyzeResponse> 分析结果
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    /**
     * 获取空间排行分析
     *
     * @param spaceRankAnalyzeRequest SpaceRankAnalyzeRequest 请求参数
     * @param loginUser               当前登录用户
     * @return List<Space> 空间列表
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);
}
