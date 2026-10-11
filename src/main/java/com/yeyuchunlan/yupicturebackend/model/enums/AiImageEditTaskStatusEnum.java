package com.yeyuchunlan.yupicturebackend.model.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * AI 图片编辑任务状态枚举，取值与百炼异步任务接口的 output.task_status 对应。
 */
@Getter
public enum AiImageEditTaskStatusEnum {

    PENDING("排队中", "PENDING"),
    RUNNING("处理中", "RUNNING"),
    SUCCEEDED("执行成功", "SUCCEEDED"),
    FAILED("执行失败", "FAILED"),
    CANCELED("已取消", "CANCELED"),
    UNKNOWN("任务不存在或状态未知", "UNKNOWN");

    private final String text;

    private final String value;

    AiImageEditTaskStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举，未知状态统一按 UNKNOWN 处理，避免前端拿到空文案
     */
    public static AiImageEditTaskStatusEnum getEnumByValue(String value) {
        if (StrUtil.isBlank(value)) {
            return UNKNOWN;
        }
        for (AiImageEditTaskStatusEnum statusEnum : AiImageEditTaskStatusEnum.values()) {
            if (statusEnum.value.equalsIgnoreCase(value)) {
                return statusEnum;
            }
        }
        return UNKNOWN;
    }

    /**
     * 是否为终态：终态任务不会再变化，无需继续轮询
     */
    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == UNKNOWN;
    }
}
