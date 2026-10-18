package com.yeyuchunlan.pixarkbackend.service;

import com.yeyuchunlan.pixarkbackend.api.imagesearch.model.ImageSearchTaskMessage;
import com.yeyuchunlan.pixarkbackend.model.entity.User;
import com.yeyuchunlan.pixarkbackend.model.vo.picture.ImageSearchTaskVO;

/**
 * 以图搜图任务服务：接口只负责入队与查状态，真正的爬取由消费者串行执行。
 */
public interface ImageSearchTaskService {

    /**
     * 创建任务并投递到队列
     */
    ImageSearchTaskVO createTask(Long pictureId, String provider, User loginUser);

    /**
     * 执行搜索（消费者调用）。业务性失败自行置为终态，只有可重试的异常才往外抛
     */
    void executeTask(ImageSearchTaskMessage message);

    /**
     * 记一次执行失败，连续累计到阈值就进入冷却期（消费者在 catch 里调）
     */
    void recordFailure();

    /**
     * 置为失败终态，供消费者在重试耗尽时调用
     */
    void markFailed(String taskId, String failReason);

    /**
     * 查询任务，仅提交人可查
     */
    ImageSearchTaskVO getTask(String taskId, User loginUser);
}
