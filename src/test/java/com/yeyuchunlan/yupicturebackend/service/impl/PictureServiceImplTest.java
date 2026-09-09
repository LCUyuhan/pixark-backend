package com.yeyuchunlan.yupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.manager.FileManager;
import com.yeyuchunlan.yupicturebackend.mapper.PictureMapper;
import com.yeyuchunlan.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.Picture;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.vo.PictureVO;
import com.yeyuchunlan.yupicturebackend.model.vo.UserVO;
import com.yeyuchunlan.yupicturebackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * {@link PictureServiceImpl} 单元测试：模拟 Mapper、FileManager 与 UserService，
 * 覆盖图片上传、查询封装、分页封装、校验等核心逻辑。
 */
class PictureServiceImplTest {

    private PictureMapper pictureMapper;
    private FileManager fileManager;
    private UserService userService;
    private PictureServiceImpl pictureService;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        pictureMapper = Mockito.mock(PictureMapper.class);
        fileManager = Mockito.mock(FileManager.class);
        userService = Mockito.mock(UserService.class);
        request = Mockito.mock(HttpServletRequest.class);
        pictureService = Mockito.spy(new PictureServiceImpl());
        ReflectionTestUtils.setField(pictureService, "baseMapper", pictureMapper);
        ReflectionTestUtils.setField(pictureService, "fileManager", fileManager);
        ReflectionTestUtils.setField(pictureService, "userService", userService);
    }

    // ---------- 上传图片 ----------

    @Test
    void uploadPicture_loginUserNull_throws() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> pictureService.uploadPicture(null, new PictureUploadRequest(), null));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), e.getCode());
    }

    @Test
    void uploadPicture_newPicture_success() {
        User loginUser = new User();
        loginUser.setId(10L);

        UploadPictureResult uploadResult = new UploadPictureResult();
        uploadResult.setUrl("https://host/public/10/a.jpg");
        uploadResult.setPicName("a");
        uploadResult.setPicSize(100L);
        uploadResult.setPicWidth(800);
        uploadResult.setPicHeight(600);
        uploadResult.setPicScale(1.33);
        uploadResult.setPicFormat("jpg");

        when(fileManager.uploadPicture(any(MultipartFile.class), eq("public/10"))).thenReturn(uploadResult);
        doReturn(true).when(pictureService).saveOrUpdate(any(Picture.class));

        PictureVO vo = pictureService.uploadPicture(Mockito.mock(MultipartFile.class), new PictureUploadRequest(), loginUser);

        assertNotNull(vo);
        assertEquals("https://host/public/10/a.jpg", vo.getUrl());
        assertEquals("a", vo.getName());
        assertEquals(10L, vo.getUserId());
    }

    @Test
    void uploadPicture_newPicture_saveFail_throws() {
        User loginUser = new User();
        loginUser.setId(10L);
        UploadPictureResult uploadResult = new UploadPictureResult();
        uploadResult.setUrl("u");
        when(fileManager.uploadPicture(any(MultipartFile.class), eq("public/10"))).thenReturn(uploadResult);
        doReturn(false).when(pictureService).saveOrUpdate(any(Picture.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> pictureService.uploadPicture(Mockito.mock(MultipartFile.class), new PictureUploadRequest(), loginUser));
        assertEquals("图片上传失败", e.getMessage());
    }

    // ---------- 查询条件 ----------

    @Test
    void getQueryWrapper_null_returnsEmptyWrapper() {
        assertNotNull(pictureService.getQueryWrapper(null));
    }

    @Test
    void getQueryWrapper_withAllFilters_returnsWrapper() {
        PictureQueryRequest req = new PictureQueryRequest();
        req.setId(1L);
        req.setName("名称");
        req.setIntroduction("简介");
        req.setCategory("分类");
        req.setTags(Arrays.asList("a", "b"));
        req.setPicSize(100L);
        req.setPicWidth(800);
        req.setPicHeight(600);
        req.setPicScale(1.33);
        req.setPicFormat("jpg");
        req.setSearchText("搜索词");
        req.setUserId(1L);
        req.setSortField("id");
        req.setSortOrder("ascend");
        QueryWrapper<Picture> wrapper = pictureService.getQueryWrapper(req);
        assertNotNull(wrapper);
    }

    // ---------- 图片脱敏封装 ----------

    @Test
    void getPictureVO_userIdNull_doesNotQueryUser() {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setName("图片");
        PictureVO vo = pictureService.getPictureVO(picture, request);
        assertNotNull(vo);
        assertEquals("图片", vo.getName());
        assertNull(vo.getUser());
    }

    @Test
    void getPictureVO_withUser_fillsUserVO() {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setUserId(5L);
        User user = new User();
        user.setId(5L);
        user.setUserName("u");
        UserVO userVO = new UserVO();
        userVO.setId(5L);
        userVO.setUserName("u");

        when(userService.getById(5L)).thenReturn(user);
        when(userService.getUserVO(user)).thenReturn(userVO);

        PictureVO vo = pictureService.getPictureVO(picture, request);
        assertEquals(5L, vo.getUser().getId());
    }

    // ---------- 分页封装 ----------

    @Test
    void getPictureVOPage_empty_returnsEmptyPage() {
        Page<Picture> page = new Page<>(1, 10, 0);
        Page<PictureVO> voPage = pictureService.getPictureVOPage(page, request);
        assertEquals(0, voPage.getTotal());
        assertTrue(voPage.getRecords().isEmpty());
    }

    @Test
    void getPictureVOPage_withRecords_fillsUser() {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setUserId(5L);
        picture.setName("图片");
        Page<Picture> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(picture));

        User user = new User();
        user.setId(5L);
        user.setUserName("u");
        UserVO userVO = new UserVO();
        userVO.setId(5L);
        userVO.setUserName("u");

        when(userService.listByIds(anyCollection())).thenReturn(Collections.singletonList(user));
        when(userService.getUserVO(user)).thenReturn(userVO);

        Page<PictureVO> voPage = pictureService.getPictureVOPage(page, request);
        assertEquals(1, voPage.getRecords().size());
        assertEquals(5L, voPage.getRecords().get(0).getUser().getId());
    }

    // ---------- 图片校验 ----------

    @Test
    void validPicture_null_throws() {
        BusinessException e = assertThrows(BusinessException.class, () -> pictureService.validPicture(null));
        assertEquals("图片不能为空", e.getMessage());
    }

    @Test
    void validPicture_idNull_throws() {
        assertThrows(BusinessException.class, () -> pictureService.validPicture(new Picture()));
    }

    @Test
    void validPicture_urlTooLong_throws() {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setUrl(String.join("", Collections.nCopies(1025, "a")));
        BusinessException e = assertThrows(BusinessException.class, () -> pictureService.validPicture(picture));
        assertEquals("url 过长", e.getMessage());
    }

    @Test
    void validPicture_introductionTooLong_throws() {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setIntroduction(String.join("", Collections.nCopies(801, "a")));
        BusinessException e = assertThrows(BusinessException.class, () -> pictureService.validPicture(picture));
        assertEquals("简介过长", e.getMessage());
    }

    @Test
    void validPicture_valid_doesNotThrow() {
        Picture picture = new Picture();
        picture.setId(1L);
        picture.setUrl("url");
        picture.setIntroduction("简介");
        pictureService.validPicture(picture);
    }
}