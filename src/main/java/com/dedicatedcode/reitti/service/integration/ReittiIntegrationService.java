package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.*;
import com.dedicatedcode.reitti.model.ReittiIntegration;
import com.dedicatedcode.reitti.model.RemoteUser;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.OptimisticLockException;
import com.dedicatedcode.reitti.repository.ReittiIntegrationJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.RequestFailedException;
import com.dedicatedcode.reitti.service.RequestTemporaryFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReittiIntegrationService {
    private static final Logger log = LoggerFactory.getLogger(ReittiIntegrationService.class);
    private static final List<ReittiIntegration.Status> VALID_INTEGRATION_STATUS = List.of(ReittiIntegration.Status.ACTIVE, ReittiIntegration.Status.RECOVERABLE);

    private final String advertiseUri;
    private final ReittiIntegrationJdbcService jdbcService;
    private final RestTemplate restTemplate;
    private final AvatarService avatarService;
    private final Map<Long, String> integrationSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Long> userForSubscriptions = new ConcurrentHashMap<>();

    public ReittiIntegrationService(@Value("${reitti.server.advertise-uri}") String advertiseUri, ReittiIntegrationJdbcService jdbcService,
                                    RestTemplate restTemplate,
                                    AvatarService avatarService) {
        this.advertiseUri = advertiseUri;
        this.jdbcService = jdbcService;
        this.restTemplate = restTemplate;
        this.avatarService = avatarService;
    }

    public List<UserTimelineData> getTimelineData(User user, LocalDate selectedDate, ZoneId userTimezone) {
        return this.jdbcService
                .findAllByUser(user)
                .stream().filter(integration -> integration.isEnabled() && VALID_INTEGRATION_STATUS.contains(integration.getStatus()))
                .map(integration -> {

                    log.debug("Fetching user timeline data for [{}]", integration);
                    try {
                        RemoteUser remoteUser = handleRemoteUser(integration);
                        List<TimelineEntry> timelineEntries = loadTimeLineEntries(integration, selectedDate, userTimezone);
                        integration = update(integration.withStatus(ReittiIntegration.Status.ACTIVE).withLastUsed(LocalDateTime.now()));
                        return new UserTimelineData("remote:" + integration.getId(),
                                remoteUser.getDisplayName(),
                                this.avatarService.generateInitials(remoteUser.getDisplayName()),
                                "/reitti-integration/avatar/" + integration.getId(),
                                integration.getColor(),
                                timelineEntries,
                                String.format("/reitti-integration/raw-location-points/%d?date=%s&timezone=%s", integration.getId(), selectedDate, userTimezone));
                    } catch (RequestFailedException e) {
                        log.error("couldn't fetch user info for [{}]", integration, e);
                        update(integration.withStatus(ReittiIntegration.Status.FAILED).withLastUsed(LocalDateTime.now()).withEnabled(false));
                    } catch (RequestTemporaryFailedException e) {
                        log.warn("couldn't temporarily fetch user info for [{}]", integration, e);
                        update(integration.withStatus(ReittiIntegration.Status.RECOVERABLE).withLastUsed(LocalDateTime.now()));
                    }
                    return null;
                }).toList();
    }

    public ReittiRemoteInfo getInfo(ReittiIntegration integration) throws RequestFailedException, RequestTemporaryFailedException {
        return getInfo(integration.getUrl(), integration.getToken());
    }

    public ReittiRemoteInfo getInfo(String url, String token) throws RequestFailedException, RequestTemporaryFailedException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-TOKEN", token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String infoUrl = url.endsWith("/") ?
                url + "api/v1/reitti-integration/info" :
                url + "/api/v1/reitti-integration/info";

        try {
            ResponseEntity<ReittiRemoteInfo> remoteResponse = restTemplate.exchange(
                    infoUrl,
                    HttpMethod.GET,
                    entity,
                    ReittiRemoteInfo.class
            );

            if (remoteResponse.getStatusCode().is2xxSuccessful() && remoteResponse.getBody() != null) {
                return remoteResponse.getBody();
            } else {
                if (remoteResponse.getStatusCode().is4xxClientError()) {
                    throw new RequestFailedException(infoUrl, remoteResponse.getStatusCode(), remoteResponse.getBody());
                } else {
                    throw new RequestTemporaryFailedException(infoUrl, remoteResponse.getStatusCode(), remoteResponse.getBody());
                }
            }
        } catch (RestClientException ex) {
            throw new RequestFailedException(infoUrl, HttpStatusCode.valueOf(500), "Connection refused");
        }
    }

    public List<LocationDataRequest.LocationPoint> getRawLocationData(User user, Long integrationId, String dateStr, String timezone) {
        return this.jdbcService
                .findByIdAndUser(integrationId,user)
                .stream().filter(integration -> integration.isEnabled() && VALID_INTEGRATION_STATUS.contains(integration.getStatus()))
                .map(integration -> {

                    log.debug("Fetching raw location data for [{}]", integration);
                    try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.set("X-API-TOKEN", integration.getToken());
                        HttpEntity<String> entity = new HttpEntity<>(headers);

                        String rawLocationDataUrl = integration.getUrl().endsWith("/") ?
                                integration.getUrl() + "api/v1/raw-location-points?date={date}&timezone={timezone}" :
                                integration.getUrl() + "/api/v1/raw-location-points?date={date}&timezone={timezone}";
                        ResponseEntity<Map> remoteResponse = restTemplate.exchange(
                                rawLocationDataUrl,
                                HttpMethod.GET,
                                entity,
                                Map.class,
                                dateStr,
                                timezone
                        );

                        if (remoteResponse.getStatusCode().is2xxSuccessful() && remoteResponse.getBody() != null && remoteResponse.getBody().containsKey("points")) {
                            update(integration.withStatus(ReittiIntegration.Status.ACTIVE).withLastUsed(LocalDateTime.now()));
                            return (List<LocationDataRequest.LocationPoint>) remoteResponse.getBody().get("points");
                        } else if (remoteResponse.getStatusCode().is4xxClientError()) {
                            throw new RequestFailedException(rawLocationDataUrl, remoteResponse.getStatusCode(), remoteResponse.getBody());
                        } else {
                            throw new RequestTemporaryFailedException(rawLocationDataUrl, remoteResponse.getStatusCode(), remoteResponse.getBody());
                        }
                    } catch (RequestFailedException e) {
                        log.error("couldn't fetch user info for [{}]", integration, e);
                        update(integration.withStatus(ReittiIntegration.Status.FAILED).withLastUsed(LocalDateTime.now()).withEnabled(false));
                    } catch (RequestTemporaryFailedException e) {
                        log.warn("couldn't temporarily fetch user info for [{}]", integration, e);
                        update(integration.withStatus(ReittiIntegration.Status.RECOVERABLE).withLastUsed(LocalDateTime.now()));
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst().orElse(Collections.emptyList());

    }

    private ReittiIntegration update(ReittiIntegration integration) {
        try {
            return this.jdbcService.update(integration).orElseThrow();
        } catch (OptimisticLockException ignored) {
            log.debug("Optimistic lock has been detected for [{}]", integration);
        }
        return integration;
    }

    private List<TimelineEntry> loadTimeLineEntries(ReittiIntegration integration, LocalDate selectedDate, ZoneId userTimezone) throws RequestFailedException, RequestTemporaryFailedException {

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-TOKEN", integration.getToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String timelineUrl = integration.getUrl().endsWith("/") ?
                integration.getUrl() + "api/v1/reitti-integration/timeline?date={date}&timezone={timezone}" :
                integration.getUrl() + "/api/v1/reitti-integration/timeline?date={date}&timezone={timezone}";


        ParameterizedTypeReference<List<TimelineEntry>> typeRef = new ParameterizedTypeReference<>() {};
        ResponseEntity<List<TimelineEntry>> remoteResponse = restTemplate.exchange(
                timelineUrl,
                HttpMethod.GET,
                entity,
                typeRef,
                selectedDate,
                userTimezone.getId()
        );

        if (remoteResponse.getStatusCode().is2xxSuccessful()) {
            return remoteResponse.getBody();
        } else if (remoteResponse.getStatusCode().is4xxClientError()) {
            throw new RequestFailedException(timelineUrl, remoteResponse.getStatusCode(), remoteResponse.getBody());
        } else {
            throw new RequestTemporaryFailedException(timelineUrl, remoteResponse.getStatusCode(), remoteResponse.getBody());
        }
    }

    private RemoteUser handleRemoteUser(ReittiIntegration integration) throws RequestFailedException, RequestTemporaryFailedException {
        ReittiRemoteInfo info = getInfo(integration);
        Optional<RemoteUser> persisted = this.jdbcService.findByIntegration(integration);
        if (persisted.isEmpty() || !persisted.get().getRemoteVersion().equals(info.userInfo().version())) {
            log.debug("Storing new RemoteUser for [{}]", integration);

            String avatarUrl = integration.getUrl().endsWith("/") ?
                    integration.getUrl() + "avatars/" + info.userInfo().id() :
                    integration.getUrl() + "/avatars/" + info.userInfo().id();

            try (HttpClient httpClient = HttpClient.newHttpClient()) {
                HttpRequest avatarRequest = HttpRequest.newBuilder()
                        .uri(new URI(avatarUrl))
                        .header("X-API-TOKEN", integration.getToken())
                        .GET()
                        .build();
                HttpResponse<byte[]> avatarResponse = httpClient.send(avatarRequest, HttpResponse.BodyHandlers.ofByteArray());

                RemoteUser remoteUser = new RemoteUser(info.userInfo().id(), info.userInfo().displayName(), info.userInfo().username(), info.userInfo().version());
                if (avatarResponse.statusCode() == 200) {
                    byte[] avatarData = avatarResponse.body();
                    String mimeType = avatarResponse.headers().firstValue("Content-Type").orElse("image/jpeg");

                    log.debug("Stored avatar for remote user [{}] with MIME type [{}]", info.userInfo().id(), mimeType);
                    this.jdbcService.store(integration, remoteUser, avatarData, mimeType);
                } else {
                    throw new RequestFailedException(avatarUrl, HttpStatusCode.valueOf(avatarResponse.statusCode()), avatarResponse.body());
                }

                persisted = Optional.of(remoteUser);
            } catch (Exception e) {
                log.warn("Failed to fetch avatar for remote user [{}]", info.userInfo().id(), e);
                throw new RequestFailedException(avatarUrl, HttpStatusCode.valueOf(500), "");
            }
        }
        return persisted.get();
    }

    public void registerSubscriptionsForUser(User user) {
        log.info("Registering subscriptions for user: [{}]", user.getId());

        if (advertiseUri == null || advertiseUri.isEmpty()) {
            log.warn("Advertise URI is null or empty, remote updates are disabled. Consider setting 'reitti.server.advertise-uri'");
            return;
        }
        
        List<ReittiIntegration> activeIntegrations = getActiveIntegrationsForUser(user);
        
        for (ReittiIntegration integration : activeIntegrations) {
            try {
                registerSubscriptionOnIntegration(integration, user);
                log.debug("Successfully registered subscription for integration: [{}]", integration.getId());
            } catch (Exception | RequestFailedException e) {
                log.error("couldn't fetch user info for [{}]", integration, e);
                update(integration.withStatus(ReittiIntegration.Status.FAILED).withLastUsed(LocalDateTime.now()).withEnabled(false));
            } catch (RequestTemporaryFailedException e) {
                log.warn("couldn't temporarily fetch user info for [{}]", integration, e);
                update(integration.withStatus(ReittiIntegration.Status.RECOVERABLE).withLastUsed(LocalDateTime.now()));
            }
        }
    }

    public List<ReittiIntegration> getActiveIntegrationsForUser(User user) {
        return this.jdbcService
                .findAllByUser(user)
                .stream()
                .filter(integration -> integration.isEnabled() && VALID_INTEGRATION_STATUS.contains(integration.getStatus()))
                .toList();
    }

    private void registerSubscriptionOnIntegration(ReittiIntegration integration, User user) throws RequestFailedException, RequestTemporaryFailedException {
        if (advertiseUri == null || advertiseUri.isEmpty()) {
            log.warn("No advertise URI configured, skipping subscription registration for integration: [{}]", integration.getId());
            return;
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-TOKEN", integration.getToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
        subscriptionRequest.setCallbackUrl(advertiseUri);
        HttpEntity<SubscriptionRequest> entity = new HttpEntity<>(subscriptionRequest, headers);
        
        String subscribeUrl = integration.getUrl().endsWith("/") ?
                integration.getUrl() + "api/v1/reitti-integration/subscribe" :
                integration.getUrl() + "/api/v1/reitti-integration/subscribe";
        
        try {
            ResponseEntity<SubscriptionResponse> response = restTemplate.exchange(
                    subscribeUrl,
                    HttpMethod.POST,
                    entity,
                    SubscriptionResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Successfully subscribed to integration: [{}]", integration.getId());
                synchronized (integrationSubscriptions) {
                    this.integrationSubscriptions.put(integration.getId(), response.getBody().getSubscriptionId());
                    this.userForSubscriptions.put(response.getBody().getSubscriptionId(), user.getId());
                }
            } else if (response.getStatusCode().is4xxClientError()) {
                throw new RequestFailedException(subscribeUrl, response.getStatusCode(), response.getBody());
            } else {
                throw new RequestTemporaryFailedException(subscribeUrl, response.getStatusCode(), response.getBody());
            }
        } catch (RestClientException ex) {
            throw new RequestFailedException(subscribeUrl, HttpStatusCode.valueOf(500), "Connection refused");
        }
    }

    public void unsubscribeFromIntegrations(User user) {
        log.info("Unsubscribing from integrations for user: [{}]", user.getId());

        List<ReittiIntegration> activeIntegrations = getActiveIntegrationsForUser(user);

        for (ReittiIntegration integration : activeIntegrations) {
            String subscriptionId = integrationSubscriptions.get(integration.getId());
            if (subscriptionId != null) {
                try {
                    unsubscribeFromIntegration(integration, subscriptionId);
                    integrationSubscriptions.remove(integration.getId());
                    userForSubscriptions.remove(subscriptionId);
                    log.debug("Successfully unsubscribed from integration: [{}]", integration.getId());
                } catch (Exception | RequestFailedException e) {
                    log.warn("Failed to unsubscribe from integration: [{}]", integration.getId(), e);
                    update(integration.withStatus(ReittiIntegration.Status.FAILED).withLastUsed(LocalDateTime.now()).withEnabled(false));
                } catch (RequestTemporaryFailedException e) {
                    update(integration.withStatus(ReittiIntegration.Status.RECOVERABLE).withLastUsed(LocalDateTime.now()));
                }
            }
        }
    }

    private void unsubscribeFromIntegration(ReittiIntegration integration, String subscriptionId) throws RequestFailedException, RequestTemporaryFailedException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-TOKEN", integration.getToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String unsubscribeUrl = integration.getUrl().endsWith("/") ?
                integration.getUrl() + "api/v1/reitti-integration/subscribe/" + subscriptionId :
                integration.getUrl() + "/api/v1/reitti-integration/subscribe/" + subscriptionId;

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    unsubscribeUrl,
                    HttpMethod.DELETE,
                    entity,
                    Void.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                if (response.getStatusCode().is4xxClientError()) {
                    throw new RequestFailedException(unsubscribeUrl, response.getStatusCode(), null);
                } else {
                    throw new RequestTemporaryFailedException(unsubscribeUrl, response.getStatusCode(), null);
                }
            }
        } catch (RestClientException ex) {
            throw new RequestFailedException(unsubscribeUrl, HttpStatusCode.valueOf(500), "Connection refused");
        }
    }

    public Optional<Long> getUserIdForSubscription(String subscriptionId) {
        return Optional.ofNullable(this.userForSubscriptions.get(subscriptionId));
    }
}