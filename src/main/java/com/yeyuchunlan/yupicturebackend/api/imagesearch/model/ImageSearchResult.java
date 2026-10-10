package com.yeyuchunlan.yupicturebackend.api.imagesearch.model;

import cn.hutool.core.annotation.Alias;
import lombok.Data;

/**
 * 以图搜图统一结果模型（与供应商无关）。
 * 各实现负责把自己家的原始字段归一化到下面三个语义字段。
 */
@Data
public class ImageSearchResult {

    /**
     * 可直接用于 <img src> 展示的图片地址。
     * @Alias 用于百度原始 JSON 的反序列化映射（百度字段名为 thumbUrl）。
     */
    @Alias("thumbUrl")
    private String imageUrl;

    /**
     * 图片来源页地址，前端可点击跳转（可能为 null，如阿里云未返回来源页）。
     * @Alias 用于百度原始 JSON 的反序列化映射（百度字段名为 fromUrl）。
     */
    @Alias("fromUrl")
    private String sourceUrl;

    /**
     * 标题/图注（可能为 null）
     */
    private String title;
}
