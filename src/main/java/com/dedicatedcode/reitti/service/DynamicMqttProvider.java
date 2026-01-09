package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttIntegration;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttPayloadProcessor;
import com.dedicatedcode.reitti.service.integration.mqtt.PayloadType;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DynamicMqttProvider {
    private static final Logger log = LoggerFactory.getLogger(DynamicMqttProvider.class);
    private final MqttIntegrationJdbcService repository;
    private final Map<PayloadType, MqttPayloadProcessor> processors;
    private final ConcurrentHashMap<Long, Mqtt3AsyncClient> activeClients = new ConcurrentHashMap<>();

    // Spring injects all implementations of MqttPayloadProcessor into this map
    public DynamicMqttProvider(MqttIntegrationJdbcService repository, List<MqttPayloadProcessor> processorList) {
        this.repository = repository;
        this.processors = processorList.stream()
            .collect(Collectors.toMap(MqttPayloadProcessor::getSupportedType, p -> p));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconnectAllOnStartup() {
        repository.findByEnabledTrue().forEach(this::connectClient);
    }

    public void register(User user, MqttIntegration config) {
        // Validation: No wildcards allowed
        if (config.getTopic().contains("+") || config.getTopic().contains("#")) {
            throw new IllegalArgumentException("Reitti requires explicit topics. No wildcards allowed.");
        }

        remove(user.getId());
        repository.save(config);
        connectClient(user, config);
    }

    private void connectClient(User user, MqttIntegration config) {
        Mqtt3AsyncClient client = MqttClient.builder()
                .useMqttVersion3() // MQTT 3.1.1
                .identifier(config.getIdentifier())
                .serverHost(config.getHost())
                .serverPort(config.getPort())
                .automaticReconnectWithDefaultConfig()
                .buildAsync();

        client.connectWith()
            .simpleAuth()
                .username(config.getUsername())
                .password(config.getPassword().getBytes(StandardCharsets.UTF_8))
            .applySimpleAuth()
            .send()
            .thenAccept(ack -> {
                client.subscribeWith()
                    .topicFilter(config.getTopic())
                    .callback(publish -> dispatch(user, config, publish.getPayloadAsBytes()))
                    .send();
            });

        activeClients.put(user.getId(), client);
    }

    private void dispatch(User user, MqttIntegration config, byte[] payload) {
        MqttPayloadProcessor processor = processors.get(config.getPayloadType());
        if (processor != null) {
            processor.process(user, payload);
        } else {
            log.error("No processor found for type: {}", config.getPayloadType());
        }
    }

    public void remove(Long userId) {
        Mqtt3AsyncClient client = activeClients.remove(userId);
        if (client != null) client.disconnect();
    }
}