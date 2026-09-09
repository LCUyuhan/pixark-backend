package com.yeyuchunlan.yupicturebackend.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ErrorCode} 单元测试：校验枚举各状态码与信息。
 */
class ErrorCodeTest {

    @Test
    void getCode_returnsExpectedCodes() {
        assertEquals(0, ErrorCode.SUCCESS.getCode());
        assertEquals(40000, ErrorCode.PARAMS_ERROR.getCode());
        assertEquals(40100, ErrorCode.NOT_LOGIN_ERROR.getCode());
        assertEquals(40101, ErrorCode.NO_AUTH_ERROR.getCode());
        assertEquals(40400, ErrorCode.NOT_FOUND_ERROR.getCode());
        assertEquals(40300, ErrorCode.FORBIDDEN_ERROR.getCode());
        assertEquals(50000, ErrorCode.SYSTEM_ERROR.getCode());
        assertEquals(50001, ErrorCode.OPERATION_ERROR.getCode());
    }

    @Test
    void getMessage_returnsExpectedMessages() {
        assertEquals("ok", ErrorCode.SUCCESS.getMessage());
        assertEquals("请求参数错误", ErrorCode.PARAMS_ERROR.getMessage());
        assertEquals("系统内部异常", ErrorCode.SYSTEM_ERROR.getMessage());
    }
}