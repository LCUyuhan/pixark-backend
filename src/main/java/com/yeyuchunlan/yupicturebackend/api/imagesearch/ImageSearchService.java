package com.yeyuchunlan.yupicturebackend.api.imagesearch;

import com.yeyuchunlan.yupicturebackend.api.imagesearch.model.ImageSearchResult;

import java.util.List;

/**
 * 以图搜图服务抽象。
 * 每个第三方供应商提供各自的实现，Controller 只依赖本接口，不感知具体供应商。
 */
public interface ImageSearchService {

    /**
     * 供应商标识，用于选择器路由（如 "baidu"、"aliyun"）
     */
    String getProvider();

    /**
     * 以图搜图
     *
     * @param imageUrl 待搜索图片的 URL
     * @return 相似图片列表
     */
    List<ImageSearchResult> search(String imageUrl);
}
