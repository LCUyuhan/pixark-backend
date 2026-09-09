package com.yeyuchunlan.yupicturebackend.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link UserRoleEnum} 单元测试。
 */
class UserRoleEnumTest {

    @Test
    void getEnumByValue_empty_returnsNull() {
        assertNull(UserRoleEnum.getEnumByValue(""));
        assertNull(UserRoleEnum.getEnumByValue(null));
    }

    @Test
    void getEnumByValue_user_returnsUser() {
        assertEquals(UserRoleEnum.USER, UserRoleEnum.getEnumByValue("user"));
    }

    @Test
    void getEnumByValue_admin_returnsAdmin() {
        assertEquals(UserRoleEnum.ADMIN, UserRoleEnum.getEnumByValue("admin"));
    }

    @Test
    void getEnumByValue_unknown_returnsNull() {
        assertNull(UserRoleEnum.getEnumByValue("super_admin"));
    }

    @Test
    void getText_returnsExpectedText() {
        assertEquals("用户", UserRoleEnum.USER.getText());
        assertEquals("管理员", UserRoleEnum.ADMIN.getText());
    }

    @Test
    void getValue_returnsExpectedValue() {
        assertEquals("user", UserRoleEnum.USER.getValue());
        assertEquals("admin", UserRoleEnum.ADMIN.getValue());
    }
}