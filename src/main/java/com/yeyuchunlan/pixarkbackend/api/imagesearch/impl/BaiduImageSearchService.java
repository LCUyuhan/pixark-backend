package com.yeyuchunlan.pixarkbackend.api.imagesearch.impl;

import com.yeyuchunlan.pixarkbackend.api.imagesearch.ImageSearchApiFacade;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.ImageSearchService;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.model.ImageSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 百度以图搜图实现：委托给现有的 {@link ImageSearchApiFacade} 逆向逻辑。
 */
@Slf4j
@Service
public class BaiduImageSearchService implements ImageSearchService {

    private static final String PROVIDER = "baidu";

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public List<ImageSearchResult> search(String imageUrl) {
        // 百度原始 JSON 的 thumbUrl/fromUrl 已通过 ImageSearchResult 上的 @Alias 归一化为 imageUrl/sourceUrl
        return ImageSearchApiFacade.searchImage(imageUrl);
    }
}
