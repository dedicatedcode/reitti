package com.dedicatedcode.reitti.service.notification.producer;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.JdbcPropertyService;
import com.dedicatedcode.reitti.service.VersionService;
import com.dedicatedcode.reitti.service.notification.NotificationService;
import com.dedicatedcode.reitti.service.notification.type.NewVersionNotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Periodically asks GitHub for the latest release and notifies the administrators when a newer
 * version than the running one is available. The last announced version is persisted so a restart
 * does not announce the same release twice.
 */
@Component
@ConditionalOnProperty(name = "reitti.notifications.update-check.enabled", havingValue = "true", matchIfMissing = true)
public class UpdateCheckService {
    private static final Logger log = LoggerFactory.getLogger(UpdateCheckService.class);

    private static final String LAST_ANNOUNCED_VERSION_KEY = "notifications.last-announced-version";

    private final RestTemplate restTemplate;
    private final VersionService versionService;
    private final JdbcPropertyService propertyService;
    private final UserJdbcService userJdbcService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String releaseUrl;

    public UpdateCheckService(@Qualifier("notificationRestTemplate") RestTemplate restTemplate,
                              VersionService versionService,
                              JdbcPropertyService propertyService,
                              UserJdbcService userJdbcService,
                              NotificationService notificationService,
                              ObjectMapper objectMapper,
                              @Value("${reitti.notifications.update-check.url:https://api.github.com/repos/dedicatedcode/reitti/releases/latest}") String releaseUrl) {
        this.restTemplate = restTemplate;
        this.versionService = versionService;
        this.propertyService = propertyService;
        this.userJdbcService = userJdbcService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.releaseUrl = releaseUrl;
    }

    @Scheduled(cron = "${reitti.notifications.update-check.cron:0 0 4 * * *}")
    public void checkForNewVersion() {
        try {
            String currentVersion = versionService.getVersion();
            if (!StringUtils.hasText(currentVersion) || "development".equalsIgnoreCase(currentVersion)) {
                log.debug("Skipping update check, running a development build");
                return;
            }

            Optional<String> latestVersion = fetchLatestVersion();
            if (latestVersion.isEmpty()) {
                return;
            }

            if (compareVersions(latestVersion.get(), currentVersion) <= 0) {
                log.debug("Running version [{}] is up to date (latest [{}])", currentVersion, latestVersion.get());
                return;
            }

            Optional<String> lastAnnounced = propertyService.getProperty(LAST_ANNOUNCED_VERSION_KEY);
            if (lastAnnounced.isPresent() && lastAnnounced.get().equals(latestVersion.get())) {
                log.debug("Version [{}] was already announced", latestVersion.get());
                return;
            }

            int delivered = notifyAdmins(latestVersion.get());
            if (delivered > 0) {
                propertyService.setProperty(LAST_ANNOUNCED_VERSION_KEY, latestVersion.get());
                log.info("Announced new version [{}] to {} administrator(s)", latestVersion.get(), delivered);
            } else {
                log.info("New version [{}] available but no administrator has update notifications enabled", latestVersion.get());
            }
        } catch (Exception e) {
            log.warn("Update check failed: {}", e.getMessage());
        }
    }

    private Optional<String> fetchLatestVersion() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "reitti-update-check");
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    releaseUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.debug("Update check returned status [{}]", response.getStatusCode());
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(response.getBody());
            String tag = node.path("tag_name").asString(null);
            if (!StringUtils.hasText(tag)) {
                return Optional.empty();
            }
            return Optional.of(tag.trim());
        } catch (Exception e) {
            log.debug("Could not fetch latest release: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private int notifyAdmins(String version) {
        List<User> admins = userJdbcService.getAllUsers().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .toList();
        Map<String, Object> params = new HashMap<>();
        params.put("version", version);
        return notificationService.notifyAll(admins, NewVersionNotificationType.KEY, params);
    }

    /**
     * Compares two version strings numerically, ignoring a leading {@code v} and any suffix after a
     * dash. Unparseable versions are treated as "not newer".
     */
    static int compareVersions(String left, String right) {
        int[] a = parseVersion(left);
        int[] b = parseVersion(right);
        if (a == null || b == null) {
            return -1;
        }
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int[] parseVersion(String version) {
        if (version == null) {
            return null;
        }
        String cleaned = version.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        int dash = cleaned.indexOf('-');
        if (dash > 0) {
            cleaned = cleaned.substring(0, dash);
        }
        String[] parts = cleaned.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return result;
    }
}
