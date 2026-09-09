package com.yeyuchunlan.yupicturebackend.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.yeyuchunlan.yupicturebackend.common.BaseResponse;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.manager.CosManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileController} 单元测试：模拟 CosManager，覆盖文件上传/下载的成功与异常分支。
 */
class FileControllerTest {

    private CosManager cosManager;
    private FileController fileController;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        cosManager = Mockito.mock(CosManager.class);
        response = Mockito.mock(HttpServletResponse.class);
        fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "cosManager", cosManager);
    }

    @Test
    void testUploadFile_success() throws IOException {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.txt");

        BaseResponse<String> result = fileController.testUploadFile(file);
        assertEquals("/test/test.txt", result.getData());
    }

    @Test
    void testUploadFile_cosFailure_throws() throws IOException {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.txt");
        when(cosManager.putObject(anyString(), any(File.class))).thenThrow(new RuntimeException("cos error"));

        BusinessException e = assertThrows(BusinessException.class, () -> fileController.testUploadFile(file));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode());
    }

    @Test
    void testDownloadFile_success() throws IOException {
        COSObject cosObject = Mockito.mock(COSObject.class);
        COSObjectInputStream inputStream = Mockito.mock(COSObjectInputStream.class);
        ServletOutputStream outputStream = Mockito.mock(ServletOutputStream.class);

        when(cosManager.getObject(anyString())).thenReturn(cosObject);
        when(cosObject.getObjectContent()).thenReturn(inputStream);
        when(inputStream.read(any(byte[].class))).thenReturn(-1);
        when(response.getOutputStream()).thenReturn(outputStream);

        fileController.testDownloadFile("key", response);

        verify(outputStream).write(any(byte[].class));
        verify(outputStream).flush();
    }

    @Test
    void testDownloadFile_cosFailure_throws() {
        when(cosManager.getObject(anyString())).thenThrow(new RuntimeException("cos error"));
        assertThrows(BusinessException.class, () -> fileController.testDownloadFile("key", response));
    }
}