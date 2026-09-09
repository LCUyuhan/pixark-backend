package com.yeyuchunlan.yupicturebackend.controller;

import com.yeyuchunlan.yupicturebackend.common.BaseResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MainController} 单元测试：健康检查。
 */
class MainControllerTest {

    private final MainController controller = new MainController();

    @Test
    void health_returnsOk() {
        BaseResponse<String> response = controller.health();
        assertEquals(0, response.getCode());
        assertEquals("ok", response.getData());
    }
}