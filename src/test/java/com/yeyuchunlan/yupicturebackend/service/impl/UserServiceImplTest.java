package com.yeyuchunlan.yupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yeyuchunlan.yupicturebackend.constant.UserConstant;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.mapper.UserMapper;
import com.yeyuchunlan.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.vo.LoginUserVO;
import com.yeyuchunlan.yupicturebackend.model.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserServiceImpl} 单元测试：使用 Mockito 模拟 Mapper 与 Http 请求，
 * 覆盖注册、登录、注销、查询、脱敏、权限判断等核心业务逻辑与异常分支。
 */
class UserServiceImplTest {

    private UserMapper userMapper;
    private UserServiceImpl userService;
    private HttpServletRequest request;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        userMapper = Mockito.mock(UserMapper.class);
        request = Mockito.mock(HttpServletRequest.class);
        session = Mockito.mock(HttpSession.class);
        userService = Mockito.spy(new UserServiceImpl());
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    // ---------- 注册 ----------

    @Test
    void userRegister_nullParams_throws() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.userRegister(null, "12345678", "12345678"));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void userRegister_shortAccount_throws() {
        assertThrows(BusinessException.class,
                () -> userService.userRegister("abc", "12345678", "12345678"));
    }

    @Test
    void userRegister_shortPassword_throws() {
        assertThrows(BusinessException.class,
                () -> userService.userRegister("abcd", "1234567", "1234567"));
    }

    @Test
    void userRegister_mismatchPassword_throws() {
        assertThrows(BusinessException.class,
                () -> userService.userRegister("abcd", "12345678", "12345679"));
    }

    @Test
    void userRegister_duplicateAccount_throws() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.userRegister("abcd", "12345678", "12345678"));
        assertEquals("账号重复", e.getMessage());
    }

    @Test
    void userRegister_success_returnsUserId() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(100L);
            user.setUserPassword("encrypted");
            return true;
        }).when(userService).save(any(User.class));

        long id = userService.userRegister("abcd", "12345678", "12345678");
        assertEquals(100L, id);
    }

    // ---------- 密码加密 ----------

    @Test
    void getEncryptPassword_returnsMd5WithSalt() {
        String expected = DigestUtils.md5DigestAsHex(("yeyuchunlan" + "12345678").getBytes());
        assertEquals(expected, userService.getEncryptPassword("12345678"));
    }

    // ---------- 登录 ----------

    @Test
    void userLogin_nullParams_throws() {
        assertThrows(BusinessException.class,
                () -> userService.userLogin(null, "12345678", request));
    }

    @Test
    void userLogin_shortAccount_throws() {
        assertThrows(BusinessException.class,
                () -> userService.userLogin("abc", "12345678", request));
    }

    @Test
    void userLogin_shortPassword_throws() {
        assertThrows(BusinessException.class,
                () -> userService.userLogin("abcd", "1234567", request));
    }

    @Test
    void userLogin_userNotFound_throws() {
        when(userMapper.selectOne(any())).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.userLogin("abcd", "12345678", request));
        assertEquals("用户不存在或密码错误", e.getMessage());
    }

    @Test
    void userLogin_success_returnsLoginUserVO() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount("abcd");
        user.setUserName("张三");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(request.getSession()).thenReturn(session);

        LoginUserVO vo = userService.userLogin("abcd", "12345678", request);

        assertNotNull(vo);
        assertEquals(1L, vo.getId());
        assertEquals("张三", vo.getUserName());
        verify(session).setAttribute(eq(UserConstant.USER_LOGIN_STATE), eq(user));
    }

    // ---------- 登录用户脱敏 ----------

    @Test
    void getLoginUserVO_null_returnsNull() {
        assertNull(userService.getLoginUserVO(null));
    }

    @Test
    void getLoginUserVO_nonNull_returnsCopy() {
        User user = new User();
        user.setId(1L);
        user.setUserName("张三");
        LoginUserVO vo = userService.getLoginUserVO(user);
        assertEquals(1L, vo.getId());
        assertEquals("张三", vo.getUserName());
    }

    // ---------- 获取当前登录用户 ----------

    @Test
    void getLoginUser_notLoggedIn_throws() {
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(UserConstant.USER_LOGIN_STATE)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> userService.getLoginUser(request));
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), e.getCode());
    }

    @Test
    void getLoginUser_loginUserWithoutId_throws() {
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(UserConstant.USER_LOGIN_STATE)).thenReturn(new User());
        assertThrows(BusinessException.class, () -> userService.getLoginUser(request));
    }

    @Test
    void getLoginUser_dbUserMissing_throws() {
        User sessionUser = new User();
        sessionUser.setId(1L);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(UserConstant.USER_LOGIN_STATE)).thenReturn(sessionUser);
        doReturn(null).when(userService).getById(1L);
        BusinessException e = assertThrows(BusinessException.class, () -> userService.getLoginUser(request));
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), e.getCode());
    }

    @Test
    void getLoginUser_success_returnsDbUser() {
        User sessionUser = new User();
        sessionUser.setId(1L);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(UserConstant.USER_LOGIN_STATE)).thenReturn(sessionUser);
        User dbUser = new User();
        dbUser.setId(1L);
        dbUser.setUserName("张三");
        doReturn(dbUser).when(userService).getById(1L);
        assertEquals(dbUser, userService.getLoginUser(request));
    }

    // ---------- 注销 ----------

    @Test
    void userLogout_notLoggedIn_throws() {
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(UserConstant.USER_LOGIN_STATE)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> userService.userLogout(request));
        assertEquals("未登录", e.getMessage());
    }

    @Test
    void userLogout_success_removesLoginState() {
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute(UserConstant.USER_LOGIN_STATE)).thenReturn(new User());
        assertTrue(userService.userLogout(request));
        verify(session).removeAttribute(UserConstant.USER_LOGIN_STATE);
    }

    // ---------- 用户脱敏 ----------

    @Test
    void getUserVO_null_returnsNull() {
        assertNull(userService.getUserVO(null));
    }

    @Test
    void getUserVO_nonNull_returnsCopy() {
        User user = new User();
        user.setId(1L);
        user.setUserName("张三");
        UserVO vo = userService.getUserVO(user);
        assertEquals(1L, vo.getId());
        assertEquals("张三", vo.getUserName());
    }

    @Test
    void getUserVOList_empty_returnsEmptyList() {
        assertTrue(userService.getUserVOList(new ArrayList<>()).isEmpty());
    }

    @Test
    void getUserVOList_nonEmpty_returnsMappedList() {
        User user = new User();
        user.setId(2L);
        List<UserVO> result = userService.getUserVOList(Collections.singletonList(user));
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId());
    }

    // ---------- 查询条件 ----------

    @Test
    void getQueryWrapper_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> userService.getQueryWrapper(null));
    }

    @Test
    void getQueryWrapper_withAllFilters_returnsWrapper() {
        UserQueryRequest req = new UserQueryRequest();
        req.setId(1L);
        req.setUserAccount("abc");
        req.setUserName("张三");
        req.setUserProfile("简介");
        req.setUserRole("user");
        req.setSortField("id");
        req.setSortOrder("ascend");
        QueryWrapper<User> wrapper = userService.getQueryWrapper(req);
        assertNotNull(wrapper);
    }

    @Test
    void getQueryWrapper_blankSortField_returnsWrapper() {
        UserQueryRequest req = new UserQueryRequest();
        QueryWrapper<User> wrapper = userService.getQueryWrapper(req);
        assertNotNull(wrapper);
    }

    // ---------- 权限判断 ----------

    @Test
    void isAdmin_null_returnsFalse() {
        assertFalse(userService.isAdmin(null));
    }

    @Test
    void isAdmin_admin_returnsTrue() {
        User user = new User();
        user.setUserRole("admin");
        assertTrue(userService.isAdmin(user));
    }

    @Test
    void isAdmin_normalUser_returnsFalse() {
        User user = new User();
        user.setUserRole("user");
        assertFalse(userService.isAdmin(user));
    }
}