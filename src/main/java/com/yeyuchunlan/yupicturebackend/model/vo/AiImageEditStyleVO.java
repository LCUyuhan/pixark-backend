package com.yeyuchunlan.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 风格化预设视图对象，供前端渲染风格下拉
 */
@Data
public class AiImageEditStyleVO implements Serializable {

    /**
     * 所属功能：stylization_all / stylization_local
     */
    private String function;

    /**
     * 风格名称
     */
    private String styleName;

    /**
     * 提示词模板，局部风格化含 %s 占位（作用对象），需替换后再提交
     */
    private String promptTemplate;

    private static final long serialVersionUID = 1L;
}
