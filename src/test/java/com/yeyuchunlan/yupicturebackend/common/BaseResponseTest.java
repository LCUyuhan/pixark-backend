package com.yeyuchunlan.yupicturebackend.common;

import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link BaseResponse} 单元测试：覆盖全部构造器。
 */
class BaseResponseTest {

    @Test
    void constructor_codeDataMessage() {
        BaseResponse<String> response = new BaseResponse<>(1, "d", "m");
        assertEquals(1, response.getCode());
        assertEquals("d", response.getData());
        assertEquals("m", response.getMessage());
    }

    @Test
    void constructor_codeData_defaultMessageIsBlank() {
        BaseResponse<String> response = new BaseResponse<>(1, "d");
        assertEquals(1, response.getCode());
        assertEquals("d", response.getData());
        assertEquals("", response.getMessage());
    }

    @Test
    void constructor_errorCode() {
        BaseResponse<?> response = new BaseResponse<>(ErrorCode.SYSTEM_ERROR);
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), response.getMessage());
        assertNull(response.getData());
    }
}