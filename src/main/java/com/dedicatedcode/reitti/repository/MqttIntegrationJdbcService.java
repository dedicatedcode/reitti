package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttIntegration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MqttIntegrationJdbcService {
    private final JdbcTemplate jdbcTemplate;

    public MqttIntegrationJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<MqttIntegration> findByUser(User user) {

    }

    public MqttIntegration save(User user, MqttIntegration integration) {

    }
}
