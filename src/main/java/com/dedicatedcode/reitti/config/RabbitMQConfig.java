package com.dedicatedcode.reitti.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "reitti-exchange";
    public static final String LOCATION_DATA_QUEUE = "location-data-queue";
    public static final String LOCATION_DATA_ROUTING_KEY = "location.data";
    public static final String SIGNIFICANT_PLACE_QUEUE = "significant-place-queue";
    public static final String SIGNIFICANT_PLACE_ROUTING_KEY = "significant.place.created";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue locationDataQueue() {
        return new Queue(LOCATION_DATA_QUEUE, true);
    }

    @Bean
    public Binding locationDataBinding(Queue locationDataQueue, TopicExchange exchange) {
        return BindingBuilder.bind(locationDataQueue).to(exchange).with(LOCATION_DATA_ROUTING_KEY);
    }
    
    @Bean
    public Queue significantPlaceQueue() {
        return new Queue(SIGNIFICANT_PLACE_QUEUE, true);
    }

    @Bean
    public Binding significantPlaceBinding(Queue significantPlaceQueue, TopicExchange exchange) {
        return BindingBuilder.bind(significantPlaceQueue).to(exchange).with(SIGNIFICANT_PLACE_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
