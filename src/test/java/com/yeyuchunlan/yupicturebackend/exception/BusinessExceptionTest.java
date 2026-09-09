package com.yeyuchunlan.yupicturebackend.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BusinessException} 单元测试：覆盖三个构造器的错误码与错误信息。
 */
class BusinessExceptionTest {

    @Test
    void constructor_codeAndMessage() {
        BusinessException exception = new BusinessException(40000, "参数错误");
        assertEquals(40000, exception.getCode());
        assertEquals("参数错误", exception.getMessage());
    }

    @Test
    void constructor_errorCode() {
        BusinessException exception = new BusinessException(ErrorCode.SYSTEM_ERROR);
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), exception.getCode());
        assertEquals(ErrorCode.SYSTEM_ERROR.getMessage(), exception.getMessage());
    }

    @Test
    void constructor_errorCodeAndMessage() {
        BusinessException exception = new BusinessException(ErrorCode.NOT_FOUND_ERROR, "自定义不存在");
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        assertEquals("自定义不存在", exception.getMessage());
    }
}