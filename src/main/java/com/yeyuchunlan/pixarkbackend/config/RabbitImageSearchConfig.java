package com.yeyuchunlan.pixarkbackend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 以图搜图的 RabbitMQ 拓扑：主队列（串行打百度）+ 重试队列（延迟回炉）+ 死信队列（终态）。
 *
 * 一个队列只能挂一个死信交换机，所以"延迟重试"和"最终失败"必须分两条路：
 * 重试由消费者主动投到 retry 交换机，消息在 retry 队列躺够 TTL 后，靠它自己的死信配置回到主队列；
 * 主队列挂的死信只是兜底 —— 连反序列化都失败的消息直接进 DLQ，不会被反复重投。
 */
@Configuration
public class RabbitImageSearchConfig {

    public static final String SEARCH_EXCHANGE = "image.search.exchange";
    public static final String SEARCH_QUEUE = "image.search.queue";
    public static final String SEARCH_ROUTING_KEY = "image.search";

    public static final String RETRY_EXCHANGE = "image.search.retry.exchange";
    public static final String RETRY_QUEUE = "image.search.retry.queue";
    public static final String RETRY_ROUTING_KEY = "image.search.retry";

    public static final String DLX_EXCHANGE = "image.search.dlx";
    public static final String DLQ_QUEUE = "image.search.dlq";
    public static final String DLQ_ROUTING_KEY = "image.search.dead";

    /**
     * 失败后隔多久回炉。改这个值要先在管理界面删掉 retry 队列再重启 —— 队列参数创建后不可变
     */
    private static final int RETRY_DELAY_MS = 30_000;

    @Bean
    public TopicExchange imageSearchExchange() {
        return new TopicExchange(SEARCH_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange imageSearchRetryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange imageSearchDlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue imageSearchQueue() {
        return QueueBuilder.durable(SEARCH_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue imageSearchRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(RETRY_DELAY_MS)
                .deadLetterExchange(SEARCH_EXCHANGE)
                .deadLetterRoutingKey(SEARCH_ROUTING_KEY)
                .build();
    }

    /**
     * 死信队列故意不设消费者 —— 停在这里的消息是排查线索。
     * 纪律：改 {@code ImageSearchTaskMessage} 的字段结构之前，必须先 purge 这三个队列。
     * 否则旧消息反序列化失败会被容器直接 reject 进这里，而那条任务的 Redis 状态永远停在 PENDING，
     * 没有任何人替它补写终态
     */
    @Bean
    public Queue imageSearchDeadQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Binding imageSearchBinding() {
        return BindingBuilder.bind(imageSearchQueue()).to(imageSearchExchange()).with(SEARCH_ROUTING_KEY);
    }

    @Bean
    public Binding imageSearchRetryBinding() {
        return BindingBuilder.bind(imageSearchRetryQueue()).to(imageSearchRetryExchange()).with(RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding imageSearchDlqBinding() {
        return BindingBuilder.bind(imageSearchDeadQueue()).to(imageSearchDlxExchange()).with(DLQ_ROUTING_KEY);
    }

    /**
     * 默认的消息转换器是 JDK 序列化，消息体一旦改字段就读不出来了，换成 JSON
     */
    @Bean
    public MessageConverter imageSearchMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
