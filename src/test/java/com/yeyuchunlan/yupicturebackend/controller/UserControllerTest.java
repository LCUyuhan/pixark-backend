package com.yeyuchunlan.yupicturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeyuchunlan.yupicturebackend.common.BaseResponse;
import com.yeyuchunlan.yupicturebackend.common.DeleteRequest;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.model.dto.user.UserAddRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.user.UserLoginRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.user.UserRegisterRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.user.UserUpdateRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.vo.LoginUserVO;
import com.yeyuchunlan.yupicturebackend.model.vo.UserVO;
import com.yeyuchunlan.yupicturebackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * {@link UserController} 单元测试：模拟 UserService，覆盖注册、登录、注销、增删改查等接口。
 */
class UserControllerTest {

    private UserService userService;
    private UserController userController;
    private HttpServletRequest request;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(UserService.class);
        request = Mockito.mock(HttpServletRequest.class);
        session = Mockito.mock(HttpSession.class);
        userController = new UserController();
        ReflectionTestUtils.setField(userController, "userService", userService);
    }

    @Test
    void userRegister_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> userController.userRegister(null));
    }

    @Test
    void userRegister_success() {
        UserRegisterRequest req = new UserRegisterRequest();
        req.setUserAccount("abcd");
        req.setUserPassword("12345678");
        req.setCheckPassword("12345678");
        when(userService.userRegister("abcd", "12345678", "12345678")).thenReturn(5L);

        BaseResponse<Long> response = userController.userRegister(req);
        assertEquals(0, response.getCode());
        assertEquals(5L, response.getData());
    }

    @Test
    void userLogin_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> userController.userLogin(null, request));
    }

    @Test
    void userLogin_success() {
        UserLoginRequest req = new UserLoginRequest();
        req.setUserAccount("abcd");
        req.setUserPassword("12345678");
        LoginUserVO vo = new LoginUserVO();
        vo.setId(1L);
        when(userService.userLogin("abcd", "12345678", request)).thenReturn(vo);

        assertEquals(vo, userController.userLogin(req, request).getData());
    }

    @Test
    void getLoginUser_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> userController.getLoginUser(null));
    }

    @Test
    void getLoginUser_success() {
        User user = new User();
        user.setId(1L);
        when(userService.getLoginUser(request)).thenReturn(user);
        LoginUserVO vo = new LoginUserVO();
        vo.setId(1L);
        when(userService.getLoginUserVO(user)).thenReturn(vo);

        assertEquals(vo, userController.getLoginUser(request).getData());
    }

    @Test
    void userLogout_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> userController.userLogout(null));
    }

    @Test
    void userLogout_success() {
        when(userService.userLogout(request)).thenReturn(true);
        assertTrue(Boolean.TRUE.equals(userController.userLogout(request).getData()));
    }

    @Test
    void addUser_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> userController.addUser(null));
    }

    @Test
    void addUser_success() {
        UserAddRequest req = new UserAddRequest();
        req.setUserAccount("newuser");
        req.setUserName("新用户");
        when(userService.getEncryptPassword(anyString())).thenReturn("encrypted");
        doAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(7L);
            return true;
        }).when(userService).save(any(User.class));

        BaseResponse<Long> response = userController.addUser(req);
        assertEquals(7L, response.getData());
    }

    @Test
    void addUser_saveFail_throws() {
        UserAddRequest req = new UserAddRequest();
        when(userService.getEncryptPassword(anyString())).thenReturn("encrypted");
        when(userService.save(any(User.class))).thenReturn(false);
        assertThrows(BusinessException.class, () -> userController.addUser(req));
    }

    @Test
    void getUserById_invalidId_throws() {
        assertThrows(BusinessException.class, () -> userController.getUserById(0L));
    }

    @Test
    void getUserById_notFound_throws() {
        when(userService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> userController.getUserById(1L));
    }

    @Test
    void getUserById_success() {
        User user = new User();
        user.setId(1L);
        when(userService.getById(1L)).thenReturn(user);
        assertEquals(user, userController.getUserById(1L).getData());
    }

    @Test
    void getUserVOById_success() {
        User user = new User();
        user.setId(1L);
        when(userService.getById(1L)).thenReturn(user);
        UserVO vo = new UserVO();
        vo.setId(1L);
        when(userService.getUserVO(user)).thenReturn(vo);
        assertEquals(vo, userController.getUserVOById(1L).getData());
    }

    @Test
    void deleteUser_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> userController.deleteUser(null));
    }

    @Test
    void deleteUser_invalidId_throws() {
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.setId(0L);
        assertThrows(BusinessException.class, () -> userController.deleteUser(deleteRequest));
    }

    @Test
    void deleteUser_success() {
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.setId(1L);
        when(userService.removeById(1L)).thenReturn(true);
        assertTrue(Boolean.TRUE.equals(userController.deleteUser(deleteRequest).getData()));
    }

    @Test
    void updateUser_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> userController.updateUser(null));
    }

    @Test
    void updateUser_missingId_throws() {
        assertThrows(BusinessException.class, () -> userController.updateUser(new UserUpdateRequest()));
    }

    @Test
    void updateUser_success() {
        UserUpdateRequest req = new UserUpdateRequest();
        req.setId(1L);
        req.setUserName("新名字");
        when(userService.updateById(any(User.class))).thenReturn(true);
        assertTrue(Boolean.TRUE.equals(userController.updateUser(req).getData()));
    }

    @Test
    void listUserVOByPage_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> userController.listUserVOByPage(null));
    }

    @Test
    void listUserVOByPage_success() {
        UserQueryRequest req = new UserQueryRequest();
        when(userService.getQueryWrapper(req)).thenReturn(new QueryWrapper<>());
        Page<User> userPage = new Page<>(1, 10, 0);
        userPage.setRecords(Collections.emptyList());
        when(userService.page(any(Page.class), any(QueryWrapper.class))).thenReturn(userPage);
        when(userService.getUserVOList(anyList())).thenReturn(new ArrayList<>());

        BaseResponse<Page<UserVO>> response = userController.listUserVOByPage(req);
        assertEquals(0, response.getData().getTotal());
    }
}