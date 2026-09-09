package com.yeyuchunlan.yupicturebackend.common;

import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link ResultUtils} 单元测试。
 */
class ResultUtilsTest {

    @Test
    void success_returnsOkResponse() {
        BaseResponse<String> response = ResultUtils.success("data");
        assertEquals(0, response.getCode());
        assertEquals("data", response.getData());
        assertEquals("ok", response.getMessage());
    }

    @Test
    void error_errorCode_returnsErrorResponse() {
        BaseResponse<?> response = ResultUtils.error(ErrorCode.PARAMS_ERROR);
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCode.PARAMS_ERROR.getMessage(), response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void error_codeAndMessage_returnsErrorResponse() {
        BaseResponse<?> response = ResultUtils.error(500, "服务器错误");
        assertEquals(500, response.getCode());
        assertEquals("服务器错误", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void error_errorCodeAndMessage_returnsCustomMessage() {
        BaseResponse<?> response = ResultUtils.error(ErrorCode.PARAMS_ERROR, "自定义参数错误");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), response.getCode());
        assertEquals("自定义参数错误", response.getMessage());
        assertNull(response.getData());
    }
}