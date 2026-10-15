package com.yeyuchunlan.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.exception.ThrowUtils;
import com.yeyuchunlan.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.space.SpaceEditRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.Picture;
import com.yeyuchunlan.yupicturebackend.model.entity.Space;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yeyuchunlan.yupicturebackend.model.vo.space.SpaceVO;
import com.yeyuchunlan.yupicturebackend.model.vo.user.UserVO;
import com.yeyuchunlan.yupicturebackend.service.PictureService;
import com.yeyuchunlan.yupicturebackend.service.SpaceService;
import com.yeyuchunlan.yupicturebackend.mapper.SpaceMapper;
import com.yeyuchunlan.yupicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author him
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2026-10-08 16:49:07
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{


    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private UserService userService;
    // PictureServiceImpl 已依赖 SpaceService，此处用 @Lazy 注入代理打破循环依赖
    @Resource
    @Lazy
    private PictureService pictureService;

    /**
     * 校验空间
     * @param space 空间
     * @param add 是否是新增
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }

    /**
     * 根据空间级别填充空间的限额
     * @param space 空间
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }



    /**
     * 添加空间
     * @param spaceAddRequest 空间添加请求
     * @param loginUser 登录用户
     * @return 新增空间的id
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 填充数据
        this.fillSpaceBySpaceLevel(space);
        // 数据校验
        this.validSpace(space, true);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 权限校验：读取已填充默认值的 space（spaceAddRequest.getSpaceLevel() 可能为 null，直接拆箱会 NPE）
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 1. 针对用户进行加锁 —— 用 userId 作为锁对象，保证同一用户同一时刻只能执行一次创建空间操作
        String lock = String.valueOf(userId).intern();
        // intern() 将字符串放入常量池，确保相同 userId 拿到的是同一个引用对象，才能作为同一把锁

        synchronized (lock) {
            // 2. 在锁内部使用编程式事务
            Long newSpaceId = transactionTemplate.execute(status -> {
                // 检查该用户是否已有私有空间（防止并发重复创建）
                boolean exists = this.lambdaQuery().eq(Space::getUserId, userId).exists();
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户仅能有一个私有空间");

                // 写入数据库
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

                return space.getId();  // 返回新空间 ID
            });

            // 3. Optional 处理：如果事务返回 null，则返回 -1L
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }

    /**
     * 编辑空间（仅空间创建人可用，不能修改空间级别）
     */
    @Override
    public void editSpace(SpaceEditRequest spaceEditRequest, User loginUser) {
        ThrowUtils.throwIf(spaceEditRequest == null || spaceEditRequest.getId() == null || spaceEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        long id = spaceEditRequest.getId();
        Space oldSpace = this.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 仅空间创建人和管理员
        this.checkSpaceAuth(loginUser, oldSpace);
        // 只允许编辑空间名称
        Space space = new Space();
        space.setId(id);
        space.setSpaceName(spaceEditRequest.getSpaceName());
        space.setEditTime(new java.util.Date());
        // 数据校验（非新增）
        this.validSpace(space, false);
        boolean result = this.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 删除空间
     * 级联删除空间内所有图片，并在事务提交后异步清理其 COS 文件
     */
    @Override
    public void deleteSpace(long spaceId, User loginUser) {
        ThrowUtils.throwIf(spaceId <= 0, ErrorCode.PARAMS_ERROR);
        Space oldSpace = this.getById(spaceId);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 仅空间创建人或管理员可删除
        this.checkSpaceAuth(loginUser, oldSpace);
        // 先查出空间内所有图片：事务内做逻辑删除，事务后用于清理 COS 文件
        List<Picture> spacePictures = pictureService.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .list();
        // 事务内：级联逻辑删除图片 + 删除空间，保证两者同时成功或同时回滚
        transactionTemplate.execute(status -> {
            if (CollUtil.isNotEmpty(spacePictures)) {
                List<Long> pictureIds = spacePictures.stream()
                        .map(Picture::getId)
                        .collect(Collectors.toList());
                pictureService.removeByIds(pictureIds);
            }
            boolean result = this.removeById(spaceId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            return true;
        });
        // 事务提交后：异步清理每张图片的 COS 原图与缩略图（此时图片已逻辑删除，clearPictureFile 的引用计数为 0）
        for (Picture picture : spacePictures) {
            pictureService.clearPictureFile(picture);
        }
    }

    /**
     * 将查询请求封装成 QueryWrapper 查询条件
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.eq(userId != null && userId > 0, "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(spaceLevel != null, "spaceLevel", spaceLevel);
        // 排序白名单
        Map<String, String> allowSortFieldMap = Map.of(
                "createTime", "createTime",
                "updateTime", "updateTime",
                "spaceLevel", "spaceLevel"
        );
        String sortColumn = StrUtil.isNotBlank(sortField) ? allowSortFieldMap.get(sortField) : null;
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortColumn), "ascend".equals(sortOrder), sortColumn);
        return queryWrapper;
    }

    /**
     * 获取单条空间封装对象（含用户信息）
     */
    @Override
    public SpaceVO getSpaceVO(Space space) {
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    /**
     * 分页获取空间封装对象（批量关联用户信息，避免 N+1）
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            if (userIdUserListMap.containsKey(userId)) {
                User user = userIdUserListMap.get(userId).get(0);
                spaceVO.setUser(userService.getUserVO(user));
            }
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 校验登录用户对空间的访问权限（空间创建人或管理员可用）
     */
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 仅本人或管理员可访问
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
        }
    }

}




