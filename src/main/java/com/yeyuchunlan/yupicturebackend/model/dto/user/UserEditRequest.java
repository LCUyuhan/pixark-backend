package com.yeyuchunlan.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户编辑请求（登录用户修改自己的信息）
 * 仅包含允许自助修改的字段：昵称 / 头像 / 简介。
 * 不含账号、密码、角色，从结构上杜绝自助改账号或自我提权。
 */
@Data
public class UserEditRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    private static final long serialVersionUID = 1L;
}
