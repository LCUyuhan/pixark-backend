package com.yeyuchunlan.pixarkbackend.model.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 风格化功能的预设风格清单，来自官方指南页「图像编辑-万相 2.1」。
 * 全局风格化仅支持 2 种、局部风格化支持 8 种，属于模型侧的固定能力，官方扩充后需同步维护。
 * 局部风格化的提示词需要指明作用对象，故模板中用 %s 占位，由前端替换（如「把房子变成木板风格」）。
 */
@Getter
public enum AiImageEditStyleEnum {

    FRENCH_PICTURE("法国绘本风格", "stylization_all", "转换成法国绘本风格"),
    GOLD_FOIL("金箔艺术风格", "stylization_all", "转换成金箔艺术风格"),

    ICE("冰雕", "stylization_local", "把%s变成冰雕风格"),
    CLOUD("云朵", "stylization_local", "把%s变成云朵风格"),
    CHINESE_FESTIVE_LANTERN("花灯", "stylization_local", "把%s变成花灯风格"),
    WOODEN("木板", "stylization_local", "把%s变成木板风格"),
    PORCELAIN("青花瓷", "stylization_local", "把%s变成青花瓷风格"),
    FLUFFY("毛茸茸", "stylization_local", "把%s变成毛茸茸风格"),
    WEAVING("毛线", "stylization_local", "把%s变成毛线风格"),
    BALLOON("气球", "stylization_local", "把%s变成气球风格");

    /**
     * 风格名称
     */
    private final String text;

    /**
     * 所属功能，取值见 AiImageEditFunctionEnum
     */
    private final String function;

    /**
     * 提示词模板，可直接提交给接口
     */
    private final String promptTemplate;

    AiImageEditStyleEnum(String text, String function, String promptTemplate) {
        this.text = text;
        this.function = function;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 按功能取风格清单，function 为空时返回全部
     */
    public static List<AiImageEditStyleEnum> listByFunction(String function) {
        List<AiImageEditStyleEnum> list = new ArrayList<>();
        for (AiImageEditStyleEnum styleEnum : AiImageEditStyleEnum.values()) {
            if (StrUtil.isBlank(function) || styleEnum.function.equals(function)) {
                list.add(styleEnum);
            }
        }
        return list;
    }
}
