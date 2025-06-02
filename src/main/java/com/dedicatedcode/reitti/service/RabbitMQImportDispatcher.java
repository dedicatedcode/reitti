package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RabbitMQImportDispatcher implements ImportListener{

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQImportDispatcher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void handleImport(User user, List<LocationDataRequest.LocationPoint> data) {
        // Create and publish event to RabbitMQ
        LocationDataEvent event = new LocationDataEvent(
                user.getUsername(),
                new ArrayList<>(data) // Create a copy to avoid reference issues
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
                event
        );
    }
}
