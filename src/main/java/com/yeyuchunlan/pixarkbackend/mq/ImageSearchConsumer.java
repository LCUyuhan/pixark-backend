package com.yeyuchunlan.pixarkbackend.mq;

import com.rabbitmq.client.Channel;
import com.yeyuchunlan.pixarkbackend.api.imagesearch.model.ImageSearchTaskMessage;
import com.yeyuchunlan.pixarkbackend.config.ImageSearchProperties;
import com.yeyuchunlan.pixarkbackend.config.RabbitImageSearchConfig;
import com.yeyuchunlan.pixarkbackend.service.ImageSearchTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 以图搜图消费者。yml 里 concurrency=1 且 prefetch=1，所以同一时刻只有一个任务在打百度，
 * 前一个没 ack 之前 broker 不会投第二条过来 —— 这就是"不并发爬图"的落点。
 */
@Slf4j
@Component
public class ImageSearchConsumer {

    @Resource
    private ImageSearchTaskService imageSearchTaskService;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private ImageSearchProperties imageSearchProperties;

    @RabbitListener(queues = RabbitImageSearchConfig.SEARCH_QUEUE)
    public void onSearchMessage(ImageSearchTaskMessage taskMessage, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            imageSearchTaskService.executeTask(taskMessage);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("以图搜图任务执行失败: taskId = {}, retryCount = {}",
                    taskMessage.getTaskId(), taskMessage.getRetryCount(), e);
            // 累计连续失败次数，到阈值就让全局进冷却，后面一段时间谁都不去碰百度
            imageSearchTaskService.recordFailure();
            handleFailure(taskMessage);
            // 不管转投重试还是进死信，原消息都必须 ack。不 ack 它会被立刻重投回本队列，变成热循环
            ackQuietly(channel, deliveryTag);
        }
    }

    /**
     * 没到重试上限就丢进重试队列（在那儿躺 30 秒再由死信配置送回主队列），到顶了就进死信队列并置任务失败
     */
    private void handleFailure(ImageSearchTaskMessage taskMessage) {
        int retryCount = taskMessage.getRetryCount() == null ? 0 : taskMessage.getRetryCount();
        int nextRetry = retryCount + 1;
        taskMessage.setRetryCount(nextRetry);
        if (nextRetry <= imageSearchProperties.getMaxRetry()) {
            rabbitTemplate.convertAndSend(RabbitImageSearchConfig.RETRY_EXCHANGE,
                    RabbitImageSearchConfig.RETRY_ROUTING_KEY, taskMessage);
            return;
        }
        rabbitTemplate.convertAndSend(RabbitImageSearchConfig.DLX_EXCHANGE,
                RabbitImageSearchConfig.DLQ_ROUTING_KEY, taskMessage);
        imageSearchTaskService.markFailed(taskMessage.getTaskId(), "搜索失败，请稍后重试");
    }

    private void ackQuietly(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException ioException) {
            log.error("以图搜图任务 ack 失败: deliveryTag = {}", deliveryTag, ioException);
        }
    }
}
