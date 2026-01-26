package com.algomesti.pocminio.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String QUEUE_NAME = "alerts.queue";

    @Bean
    public Queue alertsQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    // ADICIONE ESTE BEAN ABAIXO:
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}