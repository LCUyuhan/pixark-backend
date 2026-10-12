package com.yeyuchunlan.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;
/**
 * 更新用户请求
 *
 * @author yeyuchun
 */
@Data
public class UserUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * AI 图片编辑剩余额度（次数），仅管理员可改。
     * 留空则不更新该字段（MyBatis-Plus 默认只更新非 null 字段），避免改昵称时把额度误清零
     */
    private Integer aiQuota;

    private static final long serialVersionUID = 1L;
}
