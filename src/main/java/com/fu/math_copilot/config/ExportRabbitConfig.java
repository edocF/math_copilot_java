package com.fu.math_copilot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExportMqProperties.class)
public class ExportRabbitConfig {

    public static final String MAIN_EXCHANGE = "math.exam.export.exchange";
    public static final String MAIN_QUEUE = "math.exam.export.queue";
    public static final String MAIN_ROUTING_KEY = "export.request";

    public static final String RETRY_EXCHANGE = "math.exam.export.retry.exchange";
    public static final String RETRY_QUEUE = "math.exam.export.retry.queue";
    public static final String RETRY_ROUTING_KEY = "export.retry";

    public static final String DEAD_EXCHANGE = "math.exam.export.dead.exchange";
    public static final String DEAD_QUEUE = "math.exam.export.dead.queue";
    public static final String DEAD_ROUTING_KEY = "export.dead";

    private final ExportMqProperties properties;

    public ExportRabbitConfig(ExportMqProperties properties) {
        this.properties = properties;
    }

    @Bean
    public DirectExchange exportMainExchange() {
        return new DirectExchange(MAIN_EXCHANGE, true, false);
    }

    @Bean
    public Queue exportMainQueue() {
        return QueueBuilder.durable(MAIN_QUEUE).build();
    }

    @Bean
    public Binding exportMainBinding() {
        return BindingBuilder.bind(exportMainQueue())
                .to(exportMainExchange())
                .with(MAIN_ROUTING_KEY);
    }

    @Bean
    public DirectExchange exportRetryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    public Queue exportRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .withArgument("x-message-ttl", properties.getRetryDelayMillis())
                .deadLetterExchange(MAIN_EXCHANGE)
                .deadLetterRoutingKey(MAIN_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding exportRetryBinding() {
        return BindingBuilder.bind(exportRetryQueue())
                .to(exportRetryExchange())
                .with(RETRY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange exportDeadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue exportDeadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    public Binding exportDeadBinding() {
        return BindingBuilder.bind(exportDeadQueue())
                .to(exportDeadExchange())
                .with(DEAD_ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
