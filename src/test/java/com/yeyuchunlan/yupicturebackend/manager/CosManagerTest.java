package com.yeyuchunlan.yupicturebackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.yeyuchunlan.yupicturebackend.config.CosClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link CosManager} 单元测试：模拟 COSClient，覆盖对象上传/下载/图片上传的委托逻辑。
 */
class CosManagerTest {

    private CosClientConfig cosClientConfig;
    private COSClient cosClient;
    private CosManager cosManager;

    @BeforeEach
    void setUp() {
        cosClientConfig = Mockito.mock(CosClientConfig.class);
        cosClient = Mockito.mock(COSClient.class);
        cosManager = new CosManager();
        ReflectionTestUtils.setField(cosManager, "cosClientConfig", cosClientConfig);
        ReflectionTestUtils.setField(cosManager, "cosClient", cosClient);
        when(cosClientConfig.getBucket()).thenReturn("test-bucket");
    }

    @Test
    void putObject_delegatesToClient() {
        PutObjectResult expected = Mockito.mock(PutObjectResult.class);
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(expected);
        assertEquals(expected, cosManager.putObject("key", new File("x.txt")));
    }

    @Test
    void getObject_delegatesToClient() {
        COSObject expected = Mockito.mock(COSObject.class);
        when(cosClient.getObject(any(GetObjectRequest.class))).thenReturn(expected);
        assertEquals(expected, cosManager.getObject("key"));
    }

    @Test
    void putPictureObject_delegatesToClient() {
        PutObjectResult expected = Mockito.mock(PutObjectResult.class);
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(expected);
        assertEquals(expected, cosManager.putPictureObject("key", new File("x.txt")));
    }
}