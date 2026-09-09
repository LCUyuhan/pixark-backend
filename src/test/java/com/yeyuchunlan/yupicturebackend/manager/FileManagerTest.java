package com.yeyuchunlan.yupicturebackend.manager;

import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.OriginalInfo;
import com.yeyuchunlan.yupicturebackend.config.CosClientConfig;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.model.dto.file.UploadPictureResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link FileManager} 单元测试：模拟 MultipartFile 与 COS 依赖，
 * 覆盖文件校验、上传、删除临时文件等逻辑。
 */
class FileManagerTest {

    private CosClientConfig cosClientConfig;
    private CosManager cosManager;
    private FileManager fileManager;

    @BeforeEach
    void setUp() {
        cosClientConfig = Mockito.mock(CosClientConfig.class);
        cosManager = Mockito.mock(CosManager.class);
        fileManager = new FileManager();
        ReflectionTestUtils.setField(fileManager, "cosClientConfig", cosClientConfig);
        ReflectionTestUtils.setField(fileManager, "cosManager", cosManager);
    }

    // ---------- 文件校验 ----------

    @Test
    void validPicture_null_throws() {
        BusinessException e = assertThrows(BusinessException.class, () -> fileManager.validPicture(null));
        assertEquals("文件不能为空", e.getMessage());
    }

    @Test
    void validPicture_sizeTooLarge_throws() {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.getSize()).thenReturn(3 * 1024 * 1024L);
        BusinessException e = assertThrows(BusinessException.class, () -> fileManager.validPicture(file));
        assertEquals("文件大小不能超过 2M", e.getMessage());
    }

    @Test
    void validPicture_invalidSuffix_throws() {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.getSize()).thenReturn(100L);
        when(file.getOriginalFilename()).thenReturn("test.gif");
        BusinessException e = assertThrows(BusinessException.class, () -> fileManager.validPicture(file));
        assertEquals("文件类型错误", e.getMessage());
    }

    @Test
    void validPicture_valid_doesNotThrow() {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.getSize()).thenReturn(100L);
        when(file.getOriginalFilename()).thenReturn("test.png");
        fileManager.validPicture(file);
    }

    // ---------- 删除临时文件 ----------

    @Test
    void deleteTempFile_null_doesNothing() {
        fileManager.deleteTempFile(null);
    }

    @Test
    void deleteTempFile_nonExistingFile_doesNotThrow() {
        fileManager.deleteTempFile(new File("non_existing_temp_file_xyz.tmp"));
    }

    // ---------- 上传图片 ----------

    @Test
    void uploadPicture_success_returnsResult() {
        MultipartFile multipartFile = Mockito.mock(MultipartFile.class);
        when(multipartFile.getSize()).thenReturn(100L);
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(cosClientConfig.getHost()).thenReturn("https://test.cos.com");

        PutObjectResult putObjectResult = Mockito.mock(PutObjectResult.class);
        CIUploadResult ciUploadResult = Mockito.mock(CIUploadResult.class);
        OriginalInfo originalInfo = Mockito.mock(OriginalInfo.class);
        ImageInfo imageInfo = Mockito.mock(ImageInfo.class);

        when(cosManager.putPictureObject(anyString(), any(File.class))).thenReturn(putObjectResult);
        when(putObjectResult.getCiUploadResult()).thenReturn(ciUploadResult);
        when(ciUploadResult.getOriginalInfo()).thenReturn(originalInfo);
        when(originalInfo.getImageInfo()).thenReturn(imageInfo);
        when(imageInfo.getWidth()).thenReturn(800);
        when(imageInfo.getHeight()).thenReturn(600);
        when(imageInfo.getFormat()).thenReturn("jpg");

        UploadPictureResult result = fileManager.uploadPicture(multipartFile, "public/1");

        assertNotNull(result);
        assertEquals("test", result.getPicName());
        assertEquals(800, result.getPicWidth());
        assertEquals(600, result.getPicHeight());
        assertEquals("jpg", result.getPicFormat());
        assertTrue(result.getUrl().startsWith("https://test.cos.com/"));
    }

    @Test
    void uploadPicture_cosFailure_throwsSystemError() {
        MultipartFile multipartFile = Mockito.mock(MultipartFile.class);
        when(multipartFile.getSize()).thenReturn(100L);
        when(multipartFile.getOriginalFilename()).thenReturn("test.jpg");
        when(cosManager.putPictureObject(anyString(), any(File.class)))
                .thenThrow(new RuntimeException("cos error"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> fileManager.uploadPicture(multipartFile, "public/1"));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), e.getCode());
    }
}