package com.yeyuchunlan.yupicturebackend.manager;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yeyuchunlan.yupicturebackend.model.vo.picture.PictureVO;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 公共图库分页缓存管理器
 * 收敛「本地缓存(Caffeine) -> 分布式缓存(Redis) -> 回写」的三级缓存编排，
 * 使 Controller 只关注权限校验与查询编排，不再持有缓存基础设施。
 */
@Component
public class PictureCacheManager {

    /**
     * 公共图库分页缓存 key 前缀
     */
    private static final String LIST_CACHE_KEY_PREFIX = "yupicture:listPictureVOByPage:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // Caffeine 本地缓存：1 分钟过期，挡掉绝大部分重复请求
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    .expireAfterWrite(1L, TimeUnit.MINUTES)
                    .build();

    /**
     * 按查询条件构建缓存 key（md5 摘要，避免 key 过长）
     *
     * @param queryRequest 查询请求对象
     * @return 缓存 key
     */
    public String buildCacheKey(Object queryRequest) {
        String queryCondition = JSONUtil.toJsonStr(queryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        return LIST_CACHE_KEY_PREFIX + hashKey;
    }

    /**
     * 读取缓存（本地 -> Redis），Redis 命中会回填本地
     *
     * @param cacheKey 缓存 key
     * @return 命中的分页结果；未命中返回 null
     */
    @SuppressWarnings("unchecked")
    public Page<PictureVO> getCachePage(String cacheKey) {
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue == null) {
            cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                LOCAL_CACHE.put(cacheKey, cachedValue);
            }
        }
        if (cachedValue == null) {
            return null;
        }
        return JSONUtil.toBean(cachedValue, Page.class);
    }

    /**
     * 写入缓存（本地 + Redis），Redis 5~10 分钟随机过期，防止缓存雪崩
     *
     * @param cacheKey 缓存 key
     * @param page     待缓存的分页结果
     */
    public void putCachePage(String cacheKey, Page<PictureVO> page) {
        String cacheValue = JSONUtil.toJsonStr(page);
        LOCAL_CACHE.put(cacheKey, cacheValue);
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        stringRedisTemplate.opsForValue().set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
    }

    /**
     * 清除公共图库缓存（本地缓存 + Redis）
     * 仅在会改变「已缓存那一页内容」的操作后调用：删除公共图、审核、管理员上传公共图、批量上传、AI 结果入库
     * 私有空间的图不进缓存（列表强制 spaceId 为空且已过审），其增删改无需清
     * 已知取舍：编辑已过审公共图的名称/分类/标签不清缓存，列表文案最多滞后一个 Redis 过期时间
     */
    public void clearPublicGalleryCache() {
        // 清除本地缓存
        LOCAL_CACHE.invalidateAll();
        // 清除 Redis 中所有公共图库分页缓存（用 SCAN 分批扫描，避免 KEYS 阻塞 Redis 单线程）
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(LIST_CACHE_KEY_PREFIX + "*")
                .count(100)
                .build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
