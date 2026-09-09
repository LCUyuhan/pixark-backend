package com.yeyuchunlan.yupicturebackend.exception;

import com.yeyuchunlan.yupicturebackend.common.BaseResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link GlobalExceptionHandler} 单元测试。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionHandler_returnsErrorResponse() {
        BaseResponse<?> response = handler.businessExceptionHandler(new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getMessage(), response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void runtimeExceptionHandler_returnsSystemError() {
        BaseResponse<?> response = handler.runtimeExceptionHandler(new RuntimeException("boom"));
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), response.getCode());
        assertEquals("系统错误", response.getMessage());
        assertNull(response.getData());
    }
}