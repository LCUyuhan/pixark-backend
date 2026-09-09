package com.yeyuchunlan.yupicturebackend.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ThrowUtils} 单元测试：覆盖三种重载在“条件成立/不成立”两种分支下的行为。
 */
class ThrowUtilsTest {

    @Test
    void throwIf_conditionTrue_throwsGivenException() {
        RuntimeException exception = new RuntimeException("boom");
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> ThrowUtils.throwIf(true, exception));
        assertEquals("boom", thrown.getMessage());
    }

    @Test
    void throwIf_conditionFalse_doesNotThrow() {
        assertDoesNotThrow(() -> ThrowUtils.throwIf(false, new RuntimeException("boom")));
    }

    @Test
    void throwIf_errorCode_conditionTrue_throwsBusinessException() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
        assertEquals(ErrorCode.PARAMS_ERROR.getMessage(), thrown.getMessage());
    }

    @Test
    void throwIf_errorCode_conditionFalse_doesNotThrow() {
        assertDoesNotThrow(() -> ThrowUtils.throwIf(false, ErrorCode.PARAMS_ERROR));
    }

    @Test
    void throwIf_errorCodeAndMessage_conditionTrue_throwsWithCustomMessage() {
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "自定义错误"));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), thrown.getCode());
        assertEquals("自定义错误", thrown.getMessage());
    }

    @Test
    void throwIf_errorCodeAndMessage_conditionFalse_doesNotThrow() {
        assertDoesNotThrow(() -> ThrowUtils.throwIf(false, ErrorCode.PARAMS_ERROR, "自定义错误"));
    }
}