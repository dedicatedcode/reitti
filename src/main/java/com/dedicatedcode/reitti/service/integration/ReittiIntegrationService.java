package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.ReittiRemoteInfo;
import com.dedicatedcode.reitti.dto.UserTimelineData;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class ReittiIntegrationService {
    private static final Logger log = LoggerFactory.getLogger(ReittiIntegrationService.class);
    private static final List<ReittiIntegration.Status> VALID_INTEGRATION_STATUS = List.of(ReittiIntegration.Status.ACTIVE, ReittiIntegration.Status.RECOVERABLE);

    private final ReittiIntegrationJdbcService jdbcService;

    private final RestTemplate restTemplate;
    private final AvatarService avatarService;

    public ReittiIntegrationService(ReittiIntegrationJdbcService jdbcService,
                                    RestTemplate restTemplate, AvatarService avatarService) {
        this.jdbcService = jdbcService;
        this.restTemplate = restTemplate;
        this.avatarService = avatarService;
    }

    public List<UserTimelineData> getTimelineData(User user) {
        this.jdbcService
                .findAllByUser(user)
                .stream().filter(integration -> integration.isEnabled() && VALID_INTEGRATION_STATUS.contains(integration.getStatus()))
                .map(integration -> {

                    log.debug("Fetching user timeline data for [{}]", integration);
                    try {
                        ReittiRemoteInfo info = getInfo(integration);
                        Optional<RemoteUser> persisted = this.jdbcService.findByIntegration(integration);
                        if (persisted.isEmpty() || !persisted.get().getRemoteVersion().equals(info.userInfo().version())) {
                            log.debug("Storing new RemoteUser for [{}]", integration);
                            
                            // Fetch avatar binary data and MIME type
                            try {
                                String avatarUrl = integration.getUrl().endsWith("/") ?
                                    integration.getUrl() + "avatars/" + info.userInfo().id() :
                                    integration.getUrl() + "/avatars/" + info.userInfo().id();
                                
                                HttpClient httpClient = HttpClient.newHttpClient();
                                HttpRequest avatarRequest = HttpRequest.newBuilder()
                                    .uri(new URI(avatarUrl))
                                    .header("X-API-TOKEN", integration.getToken())
                                    .GET()
                                    .build();
                                
                                HttpResponse<byte[]> avatarResponse = httpClient.send(avatarRequest, HttpResponse.BodyHandlers.ofByteArray());
                                
                                if (avatarResponse.statusCode() == 200) {
                                    byte[] avatarData = avatarResponse.body();
                                    String mimeType = avatarResponse.headers().firstValue("Content-Type").orElse("image/jpeg");
                                    
                                    // Store avatar data using avatar service
                                    this.avatarService.store(info.userInfo().id(), avatarData, mimeType);
                                    log.debug("Stored avatar for remote user [{}] with MIME type [{}]", info.userInfo().id(), mimeType);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to fetch avatar for remote user [{}]", info.userInfo().id(), e);
                            }
                            
                            this.jdbcService.store(integration, new RemoteUser(info.userInfo().id(), info.userInfo().displayName(), info.userInfo().username(), info.userInfo().version()));
                        }

                        new UserTimelineData("remote:" + integration.getId(), userCache.getDisplayName(), avatarFallback, avatarUrl, integration.getColor(), entries, rawLocationPointsUrl);


                    } catch (RequestFailedException e) {
                        log.error("couldn't fetch user info for [{}]", integration, e);
                        update(integration.withStatus(ReittiIntegration.Status.FAILED).withLastUsed(LocalDateTime.now()).withEnabled(false));
                    } catch (RequestTemporaryFailedException e) {
                        log.warn("couldn't temporarily fetch user info for [{}]", integration, e);
                        update(integration.withStatus(ReittiIntegration.Status.RECOVERABLE).withLastUsed(LocalDateTime.now()));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
        return Collections.emptyList();
    }

    public ReittiRemoteInfo getInfo(ReittiIntegration integration) throws RequestFailedException, RequestTemporaryFailedException {
        ReittiRemoteInfo info;
        try {
            info = getInfo(integration.getUrl(), integration.getToken());
            update(integration.withLastUsed(LocalDateTime.now()).withStatus(ReittiIntegration.Status.ACTIVE));
        } catch (RequestFailedException e) {
            update(integration.withLastUsed(LocalDateTime.now()).withStatus(ReittiIntegration.Status.FAILED).withEnabled(false));
            throw e;
        } catch (RequestTemporaryFailedException e) {
            update(integration.withLastUsed(LocalDateTime.now()).withStatus(ReittiIntegration.Status.RECOVERABLE));
            throw e;
        }
        return info;
    }

    public ReittiRemoteInfo getInfo(String url, String token) throws RequestFailedException, RequestTemporaryFailedException {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-TOKEN", token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String infoUrl = url.endsWith("/") ?
                url + "api/v1/reitti-integration/info" :
                url + "/api/v1/reitti-integration/info";

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
    }

    private void update(ReittiIntegration integration) {
        try {
            this.jdbcService.update(integration);
        } catch (OptimisticLockException e) {
            log.error("Optimistic lock has been detected for [{}]", integration);
        }
    }


//todo replace with new logic
// Add connected users data, sorted by username
//        List<ConnectedUserAccount> connectedAccounts = userSettings.getConnectedUserAccounts();

// Sort connected users by username
//        connectedAccounts.sort(Comparator.comparing(ConnectedUserAccount::userId));

//        for (ConnectedUserAccount connectedUserAccount : connectedAccounts) {
//            Optional<User> connectedUserOpt = this.userJdbcService.findById(connectedUserAccount.userId());
//            if (connectedUserOpt.isEmpty()) {
//                log.warn("Could not find user with id {}", connectedUserAccount.userId());
//                continue;
//            }
//            User connectedUser = connectedUserOpt.get();
//            // Get connected user's timeline data for the same date
//            List<ProcessedVisit> connectedVisits = processedVisitJdbcService.findByUserAndTimeOverlap(
//                    connectedUser, startOfDay, endOfDay);
//            List<Trip> connectedTrips = tripJdbcService.findByUserAndTimeOverlap(
//                    connectedUser, startOfDay, endOfDay);
//
//            // Get connected user's unit system
//            UserSettings connectedUserSettings = userSettingsJdbcService.findByUserId(connectedUser.getId())
//                    .orElse(UserSettings.defaultSettings(connectedUser.getId()));
//
//            List<TimelineEntry> connectedUserEntries = buildTimelineEntries(connectedUser, connectedVisits, connectedTrips, userTimezone, selectedDate, connectedUserSettings.getUnitSystem());
//
//            String connectedUserAvatarUrl = this.avatarService.getInfo(user.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", connectedUser.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", connectedUser.getId()));
//            String connectedUserRawLocationPointsUrl = String.format("/api/v1/raw-location-points/%d?date=%s&timezone=%s", connectedUser.getId(), date, timezone);
//            String connectedUserInitials = generateInitials(connectedUser.getDisplayName());
//
//            allUsersData.add(new UserTimelineData(connectedUser.getId(), connectedUser.getDisplayName(), connectedUserInitials, connectedUserAvatarUrl, connectedUserAccount.color(), connectedUserEntries, connectedUserRawLocationPointsUrl));
//        }

    private static final class UserCache {

        private final Long id;
        private final Long version;
        private final String displayName;
        private final String userName;

        public UserCache(ReittiRemoteInfo.RemoteUserInfo remoteUserInfo) {
            this.id = remoteUserInfo.id();
            this.displayName = remoteUserInfo.displayName();
            this.userName = remoteUserInfo.username();
            this.version = remoteUserInfo.version();
        }

        public Long getId() {
            return id;
        }

        public Long getVersion() {
            return version;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getUserName() {
            return userName;
        }
    }

}
