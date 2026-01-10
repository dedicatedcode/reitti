package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MqttIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttIntegration;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttPayloadProcessor;
import com.dedicatedcode.reitti.service.integration.mqtt.PayloadType;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DynamicMqttProvider {
    private static final Logger log = LoggerFactory.getLogger(DynamicMqttProvider.class);
    private final UserJdbcService userJdbcService;
    private final MqttIntegrationJdbcService repository;
    private final Map<PayloadType, MqttPayloadProcessor> processors;
    private final ConcurrentHashMap<Long, Mqtt3AsyncClient> activeClients = new ConcurrentHashMap<>();

    public DynamicMqttProvider(UserJdbcService userJdbcService, MqttIntegrationJdbcService repository, List<MqttPayloadProcessor> processorList) {
        this.userJdbcService = userJdbcService;
        this.repository = repository;
        this.processors = processorList.stream()
            .collect(Collectors.toMap(MqttPayloadProcessor::getSupportedType, p -> p));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconnectAllOnStartup() {
        this.userJdbcService.getAllUsers()
                .forEach(user -> this.repository.findByUser(user)
                        .ifPresent(config -> connectClient(user, config)));
    }

    public CompletableFuture<MqttTestResult> testConnection(MqttIntegration config) {
        String tempClientId = "reitti-test-" + UUID.randomUUID().toString().substring(0, 8);

        Mqtt3AsyncClient testClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier(tempClientId)
                .serverHost(config.getHost())
                .serverPort(config.getPort())
                .buildAsync();

        Mqtt3ConnectBuilder.Send<CompletableFuture<Mqtt3ConnAck>> builder = testClient.connectWith();

        if (StringUtils.hasText(config.getUsername())) {
            builder.simpleAuth()
                    .username(config.getUsername())
                    .password(config.getPassword().getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }

        return builder.send()
                .thenCompose(ack -> {
                    // Success! Disconnect immediately
                    log.info("Test connection successful.");
                    return testClient.disconnect().thenApply(v -> new MqttTestResult(true, "Connection successful"));
                })
                .exceptionally(ex -> {
                    log.error("Test connection failed: {}", ex.getMessage());
                    return new MqttTestResult(false, ex.getMessage());
                });
    }

    public void register(User user, MqttIntegration config) {
        // Validation: No wildcards allowed
        if (config.getTopic().contains("+") || config.getTopic().contains("#")) {
            throw new IllegalArgumentException("Reitti requires explicit topics. No wildcards allowed.");
        }
        remove(user);
        connectClient(user, config);
    }

    private void connectClient(User user, MqttIntegration config) {
        log.debug("Connecting client [{}] for user {} to {} on {}", config.getIdentifier(), user.getUsername(), config.getTopic(), config.getHost());
        Mqtt3AsyncClient client = MqttClient.builder()
                .useMqttVersion3()
                .identifier(config.getIdentifier())
                .serverHost(config.getHost())
                .serverPort(config.getPort())
                .automaticReconnectWithDefaultConfig()
                .buildAsync();

        Mqtt3ConnectBuilder.Send<CompletableFuture<Mqtt3ConnAck>> builder = client.connectWith()
                .cleanSession(false);
        if (StringUtils.hasText(config.getUsername())) {
            builder.simpleAuth()
                    .username(config.getUsername())
                    .password(config.getPassword().getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth();
        }
        builder.send()
                .thenAccept(ack -> {
                    log.info("Client [{}] for user {} connected to {} on {}", config.getIdentifier(), user.getUsername(), config.getTopic(), config.getHost());
                    activeClients.put(user.getId(), client);
                    client.subscribeWith()
                            .topicFilter(config.getTopic())
                            .qos(MqttQos.AT_LEAST_ONCE)
                            .callback(publish -> dispatch(user, config, publish.getPayloadAsBytes()))
                            .send();
                }).exceptionally(throwable -> {
                    log.error("Error connecting client [{}] for user {} to {} on {}", config.getIdentifier(), user.getUsername(), config.getTopic(), config.getHost(), throwable);
                    return null;
                });
    }

    private void dispatch(User user, MqttIntegration config, byte[] payload) {
        MqttPayloadProcessor processor = processors.get(config.getPayloadType());
        if (processor != null) {
            processor.process(user, payload);
        } else {
            log.error("No processor found for type: {}", config.getPayloadType());
        }
    }

    public MqttStatus isClientConnected(User user) {
        Mqtt3AsyncClient client = activeClients.get(user.getId());
        if (client == null) {
            return MqttStatus.UNAVAILABLE;
        }

        return client.getState().isConnectedOrReconnect() ? MqttStatus.CONNECTED : MqttStatus.DISCONNECTED;
    }

    public void remove(User user) {
        Mqtt3AsyncClient client = activeClients.remove(user.getId());
        if (client != null) {
            log.info("Disconnecting mqtt client for user {}", user.getUsername());
            client.disconnect();
        }
    }

    public record MqttTestResult(boolean success, String message) {
    }

    public enum MqttStatus {
        CONNECTED, DISCONNECTED, UNAVAILABLE
    }
}
