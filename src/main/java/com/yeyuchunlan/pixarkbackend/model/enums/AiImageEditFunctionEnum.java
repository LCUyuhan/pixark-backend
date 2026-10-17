package com.yeyuchunlan.pixarkbackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;

import java.util.List;

/**
 * AI 图片编辑功能枚举，取值与百炼万相通用图像编辑接口的 input.function 一一对应。
 * 这里声明模型支持的全部功能，但实际可调用的范围由 ai-image-edit.allowed-functions 控制。
 * 接口要求 prompt 必传，且实测传空字符串会让服务端抛 StringIndexOutOfBoundsException
 * （返回 InvalidParameter: string index out of range），所以每个功能都要有默认提示词，
 * 没有默认值的功能必须让用户填。
 */
@Getter
public enum AiImageEditFunctionEnum {

    /**
     * 风格属于主观选择，不给默认值，由前端从 AiImageEditStyleEnum 生成的清单里选
     */
    STYLIZATION_ALL("全局风格化", "stylization_all", null, false),
    STYLIZATION_LOCAL("局部风格化", "stylization_local", null, false),
    DESCRIPTION_EDIT("指令编辑", "description_edit", null, false),
    DESCRIPTION_EDIT_WITH_MASK("局部重绘", "description_edit_with_mask",
            "去除指定区域的物体，用周围背景自然补全", true),
    REMOVE_WATERMARK("去文字水印", "remove_watermark", "去除图像中的文字", false),
    EXPAND("扩图", "expand", "延展画面，保持原有风格与光影", false),
    SUPER_RESOLUTION("图像超分", "super_resolution", "图像超分", false),
    COLORIZATION("图像上色", "colorization", "图像上色", false),
    DOODLE("线稿生图", "doodle", null, false),
    CONTROL_CARTOON_FEATURE("参考卡通形象生图", "control_cartoon_feature", null, false);

    private final String text;

    private final String value;

    /**
     * 用户未填提示词时使用的通用指令；为 null 表示该功能必须让用户自己描述（如风格化、指令编辑）
     */
    private final String defaultPrompt;

    /**
     * 是否必须提供涂抹区域图像（黑白蒙版）
     */
    private final boolean needMask;

    AiImageEditFunctionEnum(String text, String value, String defaultPrompt, boolean needMask) {
        this.text = text;
        this.value = value;
        this.defaultPrompt = defaultPrompt;
        this.needMask = needMask;
    }

    /**
     * 根据 value 获取枚举，不存在则返回 null
     */
    public static AiImageEditFunctionEnum getEnumByValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        for (AiImageEditFunctionEnum functionEnum : AiImageEditFunctionEnum.values()) {
            if (functionEnum.value.equals(value)) {
                return functionEnum;
            }
        }
        return null;
    }

    /**
     * 是否在允许调用的白名单内（白名单来自配置，逗号分隔）
     */
    public static boolean isAllowed(String value, List<String> allowedFunctions) {
        if (ObjUtil.isEmpty(allowedFunctions)) {
            return false;
        }
        return getEnumByValue(value) != null && allowedFunctions.contains(value);
    }
}
