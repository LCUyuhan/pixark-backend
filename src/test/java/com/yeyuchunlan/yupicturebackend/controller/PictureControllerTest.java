package com.yeyuchunlan.yupicturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeyuchunlan.yupicturebackend.common.BaseResponse;
import com.yeyuchunlan.yupicturebackend.common.DeleteRequest;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureEditRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureUpdateRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.Picture;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.vo.PictureTagCategory;
import com.yeyuchunlan.yupicturebackend.model.vo.PictureVO;
import com.yeyuchunlan.yupicturebackend.service.PictureService;
import com.yeyuchunlan.yupicturebackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * {@link PictureController} 单元测试：模拟 PictureService 与 UserService，覆盖图片增删改查、分页、编辑等接口分支。
 */
class PictureControllerTest {

    private PictureService pictureService;
    private UserService userService;
    private PictureController pictureController;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        pictureService = Mockito.mock(PictureService.class);
        userService = Mockito.mock(UserService.class);
        request = Mockito.mock(HttpServletRequest.class);
        pictureController = new PictureController();
        ReflectionTestUtils.setField(pictureController, "pictureService", pictureService);
        ReflectionTestUtils.setField(pictureController, "userService", userService);
    }

    private User mockLoginUser(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    void uploadPicture_success() {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        PictureUploadRequest uploadRequest = new PictureUploadRequest();
        User loginUser = mockLoginUser(1L);
        PictureVO vo = new PictureVO();
        vo.setId(1L);
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(pictureService.uploadPicture(eq(file), eq(uploadRequest), eq(loginUser))).thenReturn(vo);

        assertEquals(vo, pictureController.uploadPicture(file, uploadRequest, request).getData());
    }

    @Test
    void deletePicture_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> pictureController.deletePicture(null, request));
    }

    @Test
    void deletePicture_notFound_throws() {
        User loginUser = mockLoginUser(1L);
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.setId(1L);
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(pictureService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> pictureController.deletePicture(deleteRequest, request));
    }

    @Test
    void deletePicture_noPermission_throws() {
        User loginUser = mockLoginUser(1L);
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.setId(1L);
        Picture oldPicture = new Picture();
        oldPicture.setId(1L);
        oldPicture.setUserId(2L);
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(pictureService.getById(1L)).thenReturn(oldPicture);
        when(userService.isAdmin(loginUser)).thenReturn(false);
        assertThrows(BusinessException.class, () -> pictureController.deletePicture(deleteRequest, request));
    }

    @Test
    void deletePicture_success() {
        User loginUser = mockLoginUser(1L);
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.setId(1L);
        Picture oldPicture = new Picture();
        oldPicture.setId(1L);
        oldPicture.setUserId(1L);
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(pictureService.getById(1L)).thenReturn(oldPicture);
        when(pictureService.removeById(1L)).thenReturn(true);

        assertTrue(Boolean.TRUE.equals(pictureController.deletePicture(deleteRequest, request).getData()));
    }

    @Test
    void updatePicture_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> pictureController.updatePicture(null));
    }

    @Test
    void updatePicture_invalidId_throws() {
        PictureUpdateRequest req = new PictureUpdateRequest();
        req.setId(0L);
        assertThrows(BusinessException.class, () -> pictureController.updatePicture(req));
    }

    @Test
    void updatePicture_notFound_throws() {
        PictureUpdateRequest req = new PictureUpdateRequest();
        req.setId(1L);
        doNothing().when(pictureService).validPicture(any(Picture.class));
        when(pictureService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> pictureController.updatePicture(req));
    }

    @Test
    void updatePicture_success() {
        PictureUpdateRequest req = new PictureUpdateRequest();
        req.setId(1L);
        req.setName("新名称");
        req.setTags(Arrays.asList("a", "b"));
        doNothing().when(pictureService).validPicture(any(Picture.class));
        when(pictureService.getById(1L)).thenReturn(new Picture());
        when(pictureService.updateById(any(Picture.class))).thenReturn(true);

        assertTrue(Boolean.TRUE.equals(pictureController.updatePicture(req).getData()));
    }

    @Test
    void getPictureById_invalidId_throws() {
        assertThrows(BusinessException.class, () -> pictureController.getPictureById(0L, request));
    }

    @Test
    void getPictureById_notFound_throws() {
        when(pictureService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> pictureController.getPictureById(1L, request));
    }

    @Test
    void getPictureById_success() {
        Picture picture = new Picture();
        picture.setId(1L);
        when(pictureService.getById(1L)).thenReturn(picture);
        assertEquals(picture, pictureController.getPictureById(1L, request).getData());
    }

    @Test
    void getPictureVOById_invalidId_throws() {
        assertThrows(BusinessException.class, () -> pictureController.getPictureVOById(0L, request));
    }

    @Test
    void getPictureVOById_success() {
        Picture picture = new Picture();
        picture.setId(1L);
        PictureVO vo = new PictureVO();
        vo.setId(1L);
        when(pictureService.getById(1L)).thenReturn(picture);
        when(pictureService.getPictureVO(eq(picture), eq(request))).thenReturn(vo);
        assertEquals(vo, pictureController.getPictureVOById(1L, request).getData());
    }

    @Test
    void listPictureByPage_success() {
        PictureQueryRequest req = new PictureQueryRequest();
        when(pictureService.getQueryWrapper(req)).thenReturn(new QueryWrapper<>());
        Page<Picture> page = new Page<>(1, 10, 0);
        when(pictureService.page(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        assertEquals(0, pictureController.listPictureByPage(req).getData().getTotal());
    }

    @Test
    void listPictureVOByPage_sizeExceeded_throws() {
        PictureQueryRequest req = new PictureQueryRequest();
        req.setPageSize(21);
        assertThrows(BusinessException.class, () -> pictureController.listPictureVOByPage(req, request));
    }

    @Test
    void listPictureVOByPage_success() {
        PictureQueryRequest req = new PictureQueryRequest();
        when(pictureService.getQueryWrapper(req)).thenReturn(new QueryWrapper<>());
        Page<Picture> page = new Page<>(1, 10, 0);
        when(pictureService.page(any(Page.class), any(QueryWrapper.class))).thenReturn(page);
        Page<PictureVO> voPage = new Page<>(1, 10, 0);
        when(pictureService.getPictureVOPage(eq(page), eq(request))).thenReturn(voPage);

        assertEquals(0, pictureController.listPictureVOByPage(req, request).getData().getTotal());
    }

    @Test
    void editPicture_nullRequest_throws() {
        assertThrows(BusinessException.class, () -> pictureController.editPicture(null, request));
    }

    @Test
    void editPicture_noPermission_throws() {
        User loginUser = mockLoginUser(1L);
        PictureEditRequest req = new PictureEditRequest();
        req.setId(1L);
        Picture oldPicture = new Picture();
        oldPicture.setId(1L);
        oldPicture.setUserId(2L);
        doNothing().when(pictureService).validPicture(any(Picture.class));
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(pictureService.getById(1L)).thenReturn(oldPicture);
        when(userService.isAdmin(loginUser)).thenReturn(false);
        assertThrows(BusinessException.class, () -> pictureController.editPicture(req, request));
    }

    @Test
    void editPicture_success() {
        User loginUser = mockLoginUser(1L);
        PictureEditRequest req = new PictureEditRequest();
        req.setId(1L);
        req.setName("新名称");
        Picture oldPicture = new Picture();
        oldPicture.setId(1L);
        oldPicture.setUserId(1L);
        doNothing().when(pictureService).validPicture(any(Picture.class));
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(pictureService.getById(1L)).thenReturn(oldPicture);
        when(pictureService.updateById(any(Picture.class))).thenReturn(true);

        assertTrue(Boolean.TRUE.equals(pictureController.editPicture(req, request).getData()));
    }

    @Test
    void listPictureTagCategory_success() {
        BaseResponse<PictureTagCategory> response = pictureController.listPictureTagCategory();
        assertNotNull(response.getData());
        assertEquals(9, response.getData().getTagList().size());
        assertEquals(5, response.getData().getCategoryList().size());
    }
}