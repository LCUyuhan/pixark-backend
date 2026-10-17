package com.yeyuchunlan.pixarkbackend.api.aiimageedit.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 调用百炼图像编辑接口的入参对象。
 * 用对象而不是散参数，是为了新增功能时不必再改 Api 层方法签名。
 */
@Data
public class AliyunImageEditRequest implements Serializable {

    /**
     * 编辑功能，取值见 AiImageEditFunctionEnum
     */
    private String function;

    /**
     * 提示词
     */
    private String prompt;

    /**
     * 输入图像：公网 URL 或 base64 data URI
     */
    private String baseImageUrl;

    /**
     * 涂抹区域图像（黑白蒙版），仅局部重绘需要
     */
    private String maskImageUrl;

    /**
     * 接口 parameters 对象的内容，key 必须是接口要求的 snake_case
     */
    private Map<String, Object> parameters;

    private static final long serialVersionUID = 1L;
}

