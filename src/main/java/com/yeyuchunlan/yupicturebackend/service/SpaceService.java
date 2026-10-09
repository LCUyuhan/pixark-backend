package com.yeyuchunlan.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeyuchunlan.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.space.SpaceEditRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.vo.SpaceVO;

/**
* @author him
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-10-08 16:49:07
*/
public interface SpaceService extends IService<Space> {

    /**
     * 校验空间
     * @param space 空间
     * @param add 是否是新增
     */
    void validSpace(Space space, boolean add);

    /**
     * 根据空间等级填充空间
     * @param space 空间
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 添加空间
     * @param spaceAddRequest 空间添加请求
     * @param loginUser 登录用户
     * @return 新增空间的id
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 编辑空间（仅空间创建人可用，不能修改空间级别）
     * @param spaceEditRequest 空间编辑请求
     * @param loginUser 登录用户
     */
    void editSpace(SpaceEditRequest spaceEditRequest, User loginUser);

    /**
     * 删除空间（仅空间创建人或管理员可用）
     * @param spaceId 空间 id
     * @param loginUser 登录用户
     */
    void deleteSpace(long spaceId, User loginUser);

    /**
     * 获取查询条件
     * @param spaceQueryRequest 空间查询请求
     * @return 查询条件
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 获取空间封装对象（含用户信息）
     * @param space 空间实体
     * @return 空间封装对象
     */
    SpaceVO getSpaceVO(Space space);

    /**
     * 分页获取空间封装对象
     * @param spacePage 空间实体分页数据
     * @return 空间封装对象分页数据
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage);
}
