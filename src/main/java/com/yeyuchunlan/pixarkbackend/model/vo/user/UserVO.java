package com.yeyuchunlan.pixarkbackend.model.vo.user;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
/**
 * 用户视图（脱敏）
 *
 * @author yeyuchun
 */
@Data
public class UserVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 账号
     */
    private String userAccount;

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

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * AI 图片编辑剩余额度（次数），供管理员在用户管理里查看与发放
     */
    private Integer aiQuota;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}

