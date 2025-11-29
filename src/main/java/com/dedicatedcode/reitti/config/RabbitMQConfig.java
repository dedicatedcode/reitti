package com.dedicatedcode.reitti.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "reitti-exchange";
    public static final String LOCATION_DATA_QUEUE = "reitti.location.data.v2";
    public static final String LOCATION_DATA_ROUTING_KEY = "reitti.location.data.v2";
    public static final String SIGNIFICANT_PLACE_QUEUE = "reitti.place.created.v2";
    public static final String SIGNIFICANT_PLACE_ROUTING_KEY = "reitti.place.created.v2";
    public static final String RECALCULATE_TRIP_QUEUE = "reitti.trip.recalculate.v2";
    public static final String DETECT_TRIP_RECALCULATION_ROUTING_KEY = "reitti.trip.recalculate.v2";
    public static final String TRIGGER_PROCESSING_PIPELINE_QUEUE = "reitti.processing.v2";
    public static final String TRIGGER_PROCESSING_PIPELINE_ROUTING_KEY = "reitti.processing.start.v2";

    public static final String USER_EVENT_QUEUE = "reitti.user.events.v2";
    public static final String USER_EVENT_ROUTING_KEY = "reitti.user.events.updated.v2";


    public static final String DLX_NAME = "reitti.dlx.exchange";
    public static final String DLQ_NAME = "reitti.dql.v2";


    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX_NAME);
    }

    @Bean
    public Queue locationDataQueue() {
    return QueueBuilder.durable(LOCATION_DATA_QUEUE)
            .withArgument("x-dead-letter-exchange", DLX_NAME)
            .withArgument("x-dead-letter-routing-key", DLQ_NAME)
            .build();
    }

    @Bean
    public Queue recaluclateTripQueue() {
        return QueueBuilder.durable(RECALCULATE_TRIP_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .build();
    }

    @Bean
    public Queue significantPlaceQueue() {
        return QueueBuilder.durable(SIGNIFICANT_PLACE_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .build();
    }

    @Bean
    public Queue triggerProcessingQueue() {
        return QueueBuilder.nonDurable(TRIGGER_PROCESSING_PIPELINE_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .build();
    }

    @Bean
    public Queue userEventQueue() {
        return QueueBuilder.nonDurable(USER_EVENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .build();
    }

    @Bean
    public Binding locationDataBinding(Queue locationDataQueue, TopicExchange exchange) {
        return BindingBuilder.bind(locationDataQueue).to(exchange).with(LOCATION_DATA_ROUTING_KEY);
    }

    @Bean
    public Binding significantPlaceBinding(Queue significantPlaceQueue, TopicExchange exchange) {
        return BindingBuilder.bind(significantPlaceQueue).to(exchange).with(SIGNIFICANT_PLACE_ROUTING_KEY);
    }

    @Bean
    public Binding recalculateTripBinding(Queue recaluclateTripQueue , TopicExchange exchange) {
        return BindingBuilder.bind(recaluclateTripQueue).to(exchange).with(DETECT_TRIP_RECALCULATION_ROUTING_KEY);
    }

    @Bean
    public Binding triggerProcessingBinding(Queue triggerProcessingQueue, TopicExchange exchange) {
        return BindingBuilder.bind(triggerProcessingQueue).to(exchange).with(TRIGGER_PROCESSING_PIPELINE_ROUTING_KEY);
    }

    @Bean
    public Binding userEventBinding(Queue userEventQueue, TopicExchange exchange) {
        return BindingBuilder.bind(userEventQueue).to(exchange).with(USER_EVENT_ROUTING_KEY);
    }

    @Bean
    Queue deadLetterQueue() {
        return new Queue(DLQ_NAME);
    }

    @Bean
    Binding dlqBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_NAME);
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
