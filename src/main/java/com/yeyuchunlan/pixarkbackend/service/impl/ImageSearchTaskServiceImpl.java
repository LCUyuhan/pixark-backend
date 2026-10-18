package com.yeyuchunlan.pixarkbackend.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.ImageSearchSelector;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.ImageSearchService;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.model.ImageSearchResult;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.model.ImageSearchTaskMessage;
import com.yeyuchunlan.pixarkbackend.config.ImageSearchProperties;
import com.yeyuchunlan.pixarkbackend.config.RabbitImageSearchConfig;
import com.yeyuchunlan.pixarkbackend.exception.BusinessException;
import com.yeyuchunlan.pixarkbackend.exception.ErrorCode;
import com.yeyuchunlan.pixarkbackend.exception.ThrowUtils;
import com.yeyuchunlan.pixarkbackend.model.entity.Picture;
import com.yeyuchunlan.pixarkbackend.model.entity.User;
import com.yeyuchunlan.pixarkbackend.model.vo.picture.ImageSearchTaskVO;
import com.yeyuchunlan.pixarkbackend.service.ImageSearchTaskService;
import com.yeyuchunlan.pixarkbackend.service.PictureService;
import com.yeyuchunlan.pixarkbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;

/**
 * 以图搜图任务：接口侧只入队 + 读写 Redis 状态，真正的爬取由消费者串行执行。
 * 任务状态存在 Redis 而不是数据库 —— 它只有几十秒的生命周期，且没有任何东西需要按它查询。
 */
@Slf4j
@Service
public class ImageSearchTaskServiceImpl implements ImageSearchTaskService {

    private static final String TASK_KEY_PREFIX = "pixark:imageSearch:task:";
    private static final String RESULT_KEY_PREFIX = "pixark:imageSearch:result:";
    private static final String RATE_KEY_PREFIX = "pixark:imageSearch:rate:";
    private static final String FAIL_COUNT_KEY = "pixark:imageSearch:failCount";
    private static final String COOLDOWN_KEY = "pixark:imageSearch:cooldown";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private ImageSearchSelector imageSearchSelector;
    @Resource
    private ImageSearchProperties imageSearchProperties;

    @Override
    public ImageSearchTaskVO createTask(Long pictureId, String provider, User loginUser) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片 id 不合法");
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 私有图只有本人或管理员能让它的图片地址被送出去爬百度；公共图谁都能搜
        if (picture.getSpaceId() != null && !picture.getUserId().equals(loginUser.getId())
                && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权搜索该图片");
        }
        String resolvedProvider = resolveProvider(provider);
        String taskId = IdUtil.fastSimpleUUID();
        // 缓存最先查：命中就根本不碰百度，所以冷却和限流都不该拦它
        List<ImageSearchResult> cached = readResultCache(resolvedProvider, pictureId);
        if (cached != null) {
            JSONObject done = newRecord(loginUser, pictureId, STATUS_SUCCEEDED);
            done.set("results", cached);
            saveRecord(taskId, done);
            return toTaskVO(taskId, STATUS_SUCCEEDED, cached, null);
        }
        // 确认要真爬了才判断冷却：冷却期内直接拒，避免"已经被封还猛撞"
        ThrowUtils.throwIf(isCoolingDown(), ErrorCode.OPERATION_ERROR, "搜索服务暂时不可用，请稍后再试");
        checkRateLimit(loginUser.getId());
        saveRecord(taskId, newRecord(loginUser, pictureId, STATUS_PENDING));

        ImageSearchTaskMessage message = new ImageSearchTaskMessage();
        message.setTaskId(taskId);
        message.setPictureId(pictureId);
        message.setProvider(resolvedProvider);
        message.setUserId(loginUser.getId());
        message.setRetryCount(0);
        try {
            rabbitTemplate.convertAndSend(RabbitImageSearchConfig.SEARCH_EXCHANGE,
                    RabbitImageSearchConfig.SEARCH_ROUTING_KEY, message);
        } catch (RuntimeException e) {
            // broker 不可用时别留下永远 PENDING 的僵尸任务。
            // 记录必须先于投递写入：反过来会让消费者抢在写入前拿到消息、被下面的幂等闸门当成过期丢弃
            finish(taskId, STATUS_FAILED, null, "搜索服务暂时不可用，请稍后再试");
            throw e;
        }
        return toTaskVO(taskId, STATUS_PENDING, null, null);
    }

    /**
     * 消费者调用。图不存在这类"重试也没用"的失败就地置终态，只有网络/解析异常才往外抛给重试逻辑
     */
    @Override
    public void executeTask(ImageSearchTaskMessage message) {
        String taskId = message.getTaskId();
        JSONObject record = readRecord(taskId);
        // 幂等闸门：消息重投或进程重启后重复消费时，已终结的任务不会再打一次百度。
        // record 为 null 说明任务记录已过期或被清掉，同样直接丢弃，不要再去爬百度
        if (record == null || isFinished(record.getStr("taskStatus"))) {
            return;
        }
        String provider = resolveProvider(message.getProvider());
        // 排队期间同图的另一条消息可能已经把结果爬回来了，这里再查一次缓存；
        // 放在冷却判断之前，是因为命中缓存根本不碰百度，没必要被冷却拦住
        List<ImageSearchResult> cached = readResultCache(provider, message.getPictureId());
        if (cached != null) {
            finish(taskId, STATUS_SUCCEEDED, cached, null);
            return;
        }
        // 确实要爬了才判断冷却：就地失败并 ack，避免把队列变成撞墙机
        if (isCoolingDown()) {
            finish(taskId, STATUS_FAILED, null, "搜索服务暂时不可用，请稍后再试");
            return;
        }
        Picture picture = pictureService.getById(message.getPictureId());
        if (picture == null) {
            finish(taskId, STATUS_FAILED, null, "图片不存在或已被删除");
            return;
        }
        String thumbnailUrl = picture.getThumbnailUrl();
        String searchUrl = StrUtil.isNotBlank(thumbnailUrl) ? thumbnailUrl : picture.getUrl();
        if (StrUtil.isBlank(searchUrl)) {
            finish(taskId, STATUS_FAILED, null, "图片没有可用的访问地址");
            return;
        }

        record.set("taskStatus", STATUS_RUNNING);
        saveRecord(taskId, record);

        ImageSearchService imageSearchService = imageSearchSelector.pick(provider);
        List<ImageSearchResult> results = imageSearchService.search(searchUrl);

        // 结果按「服务商 + 图片」缓存，同一张图下次搜索不再爬百度；空列表也是百度的有效答复，同样缓存
        writeResultCache(provider, message.getPictureId(), results);
        clearFailures();
        record.set("taskStatus", STATUS_SUCCEEDED);
        record.set("results", results);
        saveRecord(taskId, record);
    }

    @Override
    public void markFailed(String taskId, String failReason) {
        finish(taskId, STATUS_FAILED, null, failReason);
    }

    @Override
    public ImageSearchTaskVO getTask(String taskId, User loginUser) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR, "任务 id 不能为空");
        JSONObject record = readRecord(taskId);
        ThrowUtils.throwIf(record == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在或已过期");
        ThrowUtils.throwIf(!loginUser.getId().equals(record.getLong("userId")), ErrorCode.NO_AUTH_ERROR, "无权查看该任务");

        String status = record.getStr("taskStatus");
        List<ImageSearchResult> results = null;
        JSONArray resultArray = record.getJSONArray("results");
        if (resultArray != null) {
            results = JSONUtil.toList(resultArray, ImageSearchResult.class);
        }
        return toTaskVO(taskId, status, results, record.getStr("failReason"));
    }

    /**
     * 写终态。任务记录可能已因超时被清掉，此时只记日志，不该把消费者带崩
     */
    private void finish(String taskId, String status, List<ImageSearchResult> results, String failReason) {
        JSONObject record = readRecord(taskId);
        if (record == null) {
            log.warn("以图搜图任务记录已不存在，跳过写终态: taskId = {}, status = {}", taskId, status);
            return;
        }
        record.set("taskStatus", status);
        if (results != null) {
            record.set("results", results);
        }
        if (StrUtil.isNotBlank(failReason)) {
            record.set("failReason", failReason);
        }
        saveRecord(taskId, record);
    }

    private JSONObject readRecord(String taskId) {
        String json = stringRedisTemplate.opsForValue().get(TASK_KEY_PREFIX + taskId);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        return JSONUtil.parseObj(json);
    }

    private void saveRecord(String taskId, JSONObject record) {
        stringRedisTemplate.opsForValue().set(TASK_KEY_PREFIX + taskId, record.toString(),
                Duration.ofMinutes(imageSearchProperties.getTaskTtlMinutes()));
    }

    private JSONObject newRecord(User loginUser, Long pictureId, String status) {
        JSONObject record = new JSONObject();
        record.set("userId", loginUser.getId());
        record.set("pictureId", pictureId);
        record.set("taskStatus", status);
        return record;
    }

    /**
     * 读结果缓存，null 表示未命中
     */
    private List<ImageSearchResult> readResultCache(String provider, Long pictureId) {
        String json = stringRedisTemplate.opsForValue().get(resultKey(provider, pictureId));
        if (StrUtil.isBlank(json)) {
            return null;
        }
        return JSONUtil.toList(json, ImageSearchResult.class);
    }

    private void writeResultCache(String provider, Long pictureId, List<ImageSearchResult> results) {
        stringRedisTemplate.opsForValue().set(resultKey(provider, pictureId), JSONUtil.toJsonStr(results),
                Duration.ofHours(imageSearchProperties.getResultTtlHours()));
    }

    private String resultKey(String provider, Long pictureId) {
        return RESULT_KEY_PREFIX + provider + ":" + pictureId;
    }

    private String resolveProvider(String provider) {
        return StrUtil.isBlank(provider) ? imageSearchProperties.getDefaultProvider() : provider;
    }

    /**
     * 是否处于冷却期。冷却标志就是一个带 TTL 的键，到期自动解除，不需要任何定时任务
     */
    private boolean isCoolingDown() {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(COOLDOWN_KEY));
    }

    @Override
    public void recordFailure() {
        int threshold = imageSearchProperties.getFailureThreshold();
        if (threshold <= 0) {
            return;
        }
        Long fails = stringRedisTemplate.opsForValue().increment(FAIL_COUNT_KEY);
        if (fails != null && fails >= threshold) {
            stringRedisTemplate.opsForValue().set(COOLDOWN_KEY, String.valueOf(fails),
                    Duration.ofSeconds(imageSearchProperties.getCooldownSeconds()));
            stringRedisTemplate.delete(FAIL_COUNT_KEY);
            log.warn("以图搜图连续失败 {} 次，进入 {} 秒冷却", fails, imageSearchProperties.getCooldownSeconds());
        }
    }

    /**
     * 成功一次就清零，保证阈值统计的是"连续"失败
     */
    private void clearFailures() {
        stringRedisTemplate.delete(FAIL_COUNT_KEY);
    }

    /**
     * 单用户每分钟额度。窗口 key 里带分钟数，随时间天然滚动，不需要定时清理
     */
    private void checkRateLimit(Long userId) {
        int limit = imageSearchProperties.getRatePerMinute();
        if (limit <= 0) {
            return;
        }
        String key = RATE_KEY_PREFIX + userId + ":" + (System.currentTimeMillis() / 60_000L);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, Duration.ofSeconds(120));
        ThrowUtils.throwIf(count != null && count > limit, ErrorCode.OPERATION_ERROR,
                "搜索太频繁，每分钟最多 " + limit + " 次，请稍后再试");
    }

    private boolean isFinished(String status) {
        return STATUS_SUCCEEDED.equals(status) || STATUS_FAILED.equals(status);
    }

    private ImageSearchTaskVO toTaskVO(String taskId, String status, List<ImageSearchResult> results, String failReason) {
        ImageSearchTaskVO taskVO = new ImageSearchTaskVO();
        taskVO.setTaskId(taskId);
        taskVO.setTaskStatus(status);
        taskVO.setStatusText(statusText(status));
        taskVO.setResults(results);
        taskVO.setFailReason(failReason);
        return taskVO;
    }

    private String statusText(String status) {
        if (STATUS_RUNNING.equals(status)) {
            return "正在搜索";
        }
        if (STATUS_SUCCEEDED.equals(status)) {
            return "搜索完成";
        }
        if (STATUS_FAILED.equals(status)) {
            return "搜索失败";
        }
        return "排队中";
    }
}
