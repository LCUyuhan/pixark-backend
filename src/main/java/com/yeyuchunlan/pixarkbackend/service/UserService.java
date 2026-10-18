package com.yeyuchunlan.pixarkbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yeyuchunlan.pixarkbackend.model.dto.user.UserQueryRequest;
import com.yeyuchunlan.pixarkbackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yeyuchunlan.pixarkbackend.model.vo.user.LoginUserVO;
import com.yeyuchunlan.pixarkbackend.model.vo.user.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author him
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2026-09-01 19:30:27
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 删除账号行：账号墓碑化 + 逻辑删除。
     * 只处理用户这一行，不负责空间/图片级联——级联编排见 UserDeletionManager
     *
     * @param userId 用户 id
     */
    void deleteUserAccount(long userId);

    /**
     * 获取加密密码
     *
     * @param userPassword  用户密码
     * @return 加密密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request       请求
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return 脱敏后的已登录用户信息
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取当前登录用户
     *
     * @param request 请求
     * @return 登录用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request 请求
     * @return 是否注销成功
     */
    boolean userLogout(HttpServletRequest request);


    /**
     * 获取脱敏的用户信息
     *
     * @param user 用户信息
     * @return 脱敏后的用户信息
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户列表
     * @param  userList 用户列表
     * @return 脱敏后的用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取queryWrapper形式的查询条件
     * @param  userQueryRequest 用户查询条件
     * @return queryWrapper形式的查询条件
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 是否为管理员
     *
     * @param user  用户
     * @return 是否为管理员
     */
    boolean isAdmin(User user);

}
