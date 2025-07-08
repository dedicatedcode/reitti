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
    public static final String STAY_DETECTION_QUEUE = "stay-detection-queue";
    public static final String STAY_DETECTION_ROUTING_KEY = "stay.detection.created";
    public static final String MERGE_VISIT_QUEUE = "merge-visit-queue";
    public static final String MERGE_VISIT_ROUTING_KEY = "merge.visit.created";
    public static final String SIGNIFICANT_PLACE_QUEUE = "significant-place-queue";
    public static final String SIGNIFICANT_PLACE_ROUTING_KEY = "significant.place.created";
    public static final String DETECT_TRIP_QUEUE = "detect-trip-queue";
    public static final String DETECT_TRIP_ROUTING_KEY = "detect.trip.created";
    public static final String TRIGGER_PROCESSING_PIPELINE_QUEUE = "trigger-processing-queue";
    public static final String TRIGGER_PROCESSING_PIPELINE_ROUTING_KEY = "trigger.processing.start";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue locationDataQueue() {
        return new Queue(LOCATION_DATA_QUEUE, true);
    }

    @Bean
    public Queue detectTripQueue() {
        return new Queue(DETECT_TRIP_QUEUE, true);
    }

    @Bean
    public Queue mergeVisitQueue() {
        return new Queue(MERGE_VISIT_QUEUE, true);
    }

    @Bean
    public Queue significantPlaceQueue() {
        return new Queue(SIGNIFICANT_PLACE_QUEUE, true);
    }

    @Bean
    public Queue stayDetectionQueue() {
        return new Queue(STAY_DETECTION_QUEUE, true);
    }

    @Bean
    public Queue triggerProcessingQueue() {
        return new Queue(TRIGGER_PROCESSING_PIPELINE_QUEUE, false);
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
    public Binding mergeVisitBinding(Queue mergeVisitQueue, TopicExchange exchange) {
        return BindingBuilder.bind(mergeVisitQueue).to(exchange).with(MERGE_VISIT_ROUTING_KEY);
    }

    @Bean
    public Binding detectTripBinding(Queue detectTripQueue, TopicExchange exchange) {
        return BindingBuilder.bind(detectTripQueue).to(exchange).with(DETECT_TRIP_ROUTING_KEY);
    }

    @Bean
    public Binding stayDetectionBinding(Queue stayDetectionQueue, TopicExchange exchange) {
        return BindingBuilder.bind(stayDetectionQueue).to(exchange).with(STAY_DETECTION_ROUTING_KEY);
    }

    @Bean
    public Binding triggerProcessingBinding(Queue triggerProcessingQueue, TopicExchange exchange) {
        return BindingBuilder.bind(triggerProcessingQueue).to(exchange).with(TRIGGER_PROCESSING_PIPELINE_ROUTING_KEY);
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
