package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.IntegrationTestResult;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.integration.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MqttIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.OptimisticLockException;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.ApiTokenService;
import com.dedicatedcode.reitti.service.ContextPathHolder;
import com.dedicatedcode.reitti.service.DynamicMqttProvider;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import com.dedicatedcode.reitti.service.integration.OwnTracksRecorderIntegrationService;
import com.dedicatedcode.reitti.service.integration.mqtt.MqttIntegration;
import com.dedicatedcode.reitti.service.integration.mqtt.PayloadType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/settings/integrations")
public class IntegrationsSettingsController {
    private final ContextPathHolder contextPathHolder;
    private final ApiTokenService apiTokenService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final ImmichIntegrationService immichIntegrationService;
    private final OwnTracksRecorderIntegrationService ownTracksRecorderIntegrationService;
    private final DynamicMqttProvider mqttProvider;
    private final MqttIntegrationJdbcService mqttIntegrationJdbcService;
    private final I18nService i18n;
    private final boolean dataManagementEnabled;

    public IntegrationsSettingsController(ContextPathHolder contextPathHolder,
                                          ApiTokenService apiTokenService,
                                          RawLocationPointJdbcService rawLocationPointJdbcService,
                                          ImmichIntegrationService immichIntegrationService,
                                          OwnTracksRecorderIntegrationService ownTracksRecorderIntegrationService,
                                          DynamicMqttProvider mqttProvider,
                                          MqttIntegrationJdbcService mqttIntegrationJdbcService,
                                          I18nService i18nService,
                                          @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.contextPathHolder = contextPathHolder;
        this.apiTokenService = apiTokenService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.immichIntegrationService = immichIntegrationService;
        this.ownTracksRecorderIntegrationService = ownTracksRecorderIntegrationService;
        this.mqttProvider = mqttProvider;
        this.mqttIntegrationJdbcService = mqttIntegrationJdbcService;
        this.i18n = i18nService;
        this.dataManagementEnabled = dataManagementEnabled;
    }

    @GetMapping
    public String getPage(@AuthenticationPrincipal User user,
                          @RequestParam(required = false) String openSection,
                          HttpServletRequest request,
                          Model model) {
        model.addAttribute("activeSection", "integrations");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);

        List<ApiToken> tokens = apiTokenService.getTokensForUser(user);

        // Add the first token if available
        String selectedToken = null;
        if (!tokens.isEmpty()) {
            selectedToken = tokens.getFirst().getToken();
            model.addAttribute("firstToken", selectedToken);
            model.addAttribute("hasToken", true);
        } else {
            model.addAttribute("hasToken", false);
        }

        model.addAttribute("selectedToken", selectedToken);
        model.addAttribute("tokens", tokens);

        Optional<OwnTracksRecorderIntegration> recorderIntegration = ownTracksRecorderIntegrationService.getIntegrationForUser(user);
        if (recorderIntegration.isPresent()) {
            model.addAttribute("ownTracksRecorderIntegration", recorderIntegration.get());
            model.addAttribute("hasRecorderIntegration", recorderIntegration.get().isEnabled());
        } else {
            model.addAttribute("hasRecorderIntegration", false);
        }

        Optional<MqttIntegration> mqttIntegration = this.mqttIntegrationJdbcService.findByUser(user);
        if (mqttIntegration.isPresent()) {
            model.addAttribute("mqttIntegration", mqttIntegration.get());
        } else {
            model.addAttribute("generatedClientId", "reitti-client-" + UUID.randomUUID().toString().substring(0, 8));
        }
        model.addAttribute("openSection", openSection);
        model.addAttribute("serverUrl", calculateServerUrl(request));
        model.addAttribute("contextPath", contextPathHolder.getContextPath());

        return "settings/integrations";
    }

    @GetMapping("/integrations-content")
    public String getIntegrationsContent(@AuthenticationPrincipal User currentUser,
                                         @RequestParam(required = false) String selectedToken,
                                         HttpServletRequest request,
                                         Model model,
                                         @RequestParam(required = false) String openSection) {
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);

        // Determine the token to use
        String tokenToUse = null;
        if (selectedToken != null && tokens.stream().anyMatch(t -> t.getToken().equals(selectedToken))) {
            tokenToUse = selectedToken;
        } else if (!tokens.isEmpty()) {
            tokenToUse = tokens.getFirst().getToken();
        }

        if (tokenToUse != null) {
            model.addAttribute("firstToken", tokenToUse);
            model.addAttribute("hasToken", true);
        } else {
            model.addAttribute("hasToken", false);
        }

        model.addAttribute("selectedToken", tokenToUse);
        model.addAttribute("tokens", tokens);

        Optional<OwnTracksRecorderIntegration> recorderIntegration = ownTracksRecorderIntegrationService.getIntegrationForUser(currentUser);
        if (recorderIntegration.isPresent()) {
            model.addAttribute("ownTracksRecorderIntegration", recorderIntegration.get());
            model.addAttribute("hasRecorderIntegration", recorderIntegration.get().isEnabled());
        } else {
            model.addAttribute("hasRecorderIntegration", false);
        }

        Optional<MqttIntegration> mqttIntegration = this.mqttIntegrationJdbcService.findByUser(currentUser);
        if (mqttIntegration.isPresent()) {
            model.addAttribute("mqttIntegration", mqttIntegration.get());
        } else {
            model.addAttribute("generatedClientId", "reitti-client-" + UUID.randomUUID().toString().substring(0, 8));
        }
        model.addAttribute("openSection", openSection);
        model.addAttribute("serverUrl", calculateServerUrl(request));
        model.addAttribute("contextPath", contextPathHolder.getContextPath());


        return "settings/integrations :: integrations-content";
    }

    private String calculateServerUrl(HttpServletRequest request) {
        // Build the server URL
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        StringBuilder serverUrl = new StringBuilder();
        serverUrl.append(scheme).append("://").append(serverName);

        // Only add port if it's not the default port for the scheme
        if ((scheme.equals("http") && serverPort != 80) ||
                (scheme.equals("https") && serverPort != 443)) {
            serverUrl.append(":").append(serverPort);
        }
        return serverUrl.toString();
    }

    @GetMapping("/reitti.properties")
    public ResponseEntity<String> getGpsLoggerProperties(@RequestParam String token, HttpServletRequest request) {
        String serverUrl = calculateServerUrl(request);
        String url = serverUrl + contextPathHolder.getContextPath() + "/api/v1/ingest/owntracks?token=" + token;
        String properties = "log_customurl_url=" + url + "\n" +
                            "log_customurl_method=POST\n" +
                            "log_customurl_body={\"_type\" : \"location\",\"t\": \"u\",\"acc\": \"%ACC\",\"alt\": \"%ALT\",\"batt\": \"%BATT\",\"bs\": \"%ISCHARGING\",\"lat\": \"%LAT\",\"lon\": \"%LON\",\"tst\": \"%TIMESTAMP\",\"vel\": \"%SPD\"}\n" +
                            "log_customurl_headers=Content-Type: application/json\n" +
                            "autosend_frequency_minutes=60\n" +
                            "accuracy_before_logging=25\n" +
                            "time_before_logging=15\n" +
                            "autosend_enabled=true\n";
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain")
                .body(properties);
    }

    @GetMapping("/photos-content")
    public String getPhotosContent(@AuthenticationPrincipal User user, Model model) {
        Optional<ImmichIntegration> integration = immichIntegrationService.getIntegrationForUser(user);

        if (integration.isPresent()) {
            model.addAttribute("immichIntegration", integration.get());
            model.addAttribute("hasIntegration", true);
        } else {
            model.addAttribute("hasIntegration", false);
        }

        return "fragments/photos :: photos-content";
    }

    @PostMapping("/immich-integration")
    public String saveImmichIntegration(@RequestParam String serverUrl,
                                        @RequestParam String apiToken,
                                        @RequestParam(defaultValue = "false") boolean enabled,
                                        @AuthenticationPrincipal User currentUser,
                                        Model model) {
        try {
            ImmichIntegration integration = immichIntegrationService.saveIntegration(
                    currentUser, serverUrl, apiToken, enabled);

            model.addAttribute("immichIntegration", integration);
            model.addAttribute("hasIntegration", true);
            model.addAttribute("successMessage", i18n.translate("integrations.immich.config.saved"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("integrations.immich.config.error", e.getMessage()));
            // Re-populate form with submitted values
            ImmichIntegration tempIntegration = new ImmichIntegration(serverUrl, apiToken, enabled);
            model.addAttribute("immichIntegration", tempIntegration);
            model.addAttribute("hasIntegration", true);
        }

        return "fragments/photos :: photos-content";
    }

    @PostMapping("/immich-integration/test")
    @ResponseBody
    public Map<String, Object> testImmichConnection(@RequestParam String serverUrl,
                                                    @RequestParam String apiToken) {
        Map<String, Object> response = new HashMap<>();

        try {
            IntegrationTestResult result = immichIntegrationService.testConnection(serverUrl, apiToken);

            if (result.success()) {
                response.put("success", true);
                response.put("message", i18n.translate("integrations.immich.connection.success"));
            } else {
                response.put("success", false);
                response.put("message", i18n.translate("integrations.immich.connection.failed", result.message()));
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", i18n.translate("integrations.immich.connection.failed", e.getMessage()));
        }

        return response;
    }


    @PostMapping("/owntracks-recorder-integration")
    public String saveOwnTracksRecorderIntegration(@RequestParam String baseUrl,
                                                   @RequestParam String username,
                                                   @RequestParam String deviceId,
                                                   @RequestParam(defaultValue = "false") boolean enabled,
                                                   @RequestParam String authUsername,
                                                   @RequestParam String authPassword,
                                                   @AuthenticationPrincipal User currentUser,
                                                   RedirectAttributes redirectAttributes) {
        try {
            ownTracksRecorderIntegrationService.saveIntegration(currentUser, baseUrl, username, authUsername, authPassword, deviceId, enabled);
            redirectAttributes.addFlashAttribute("successMessage", i18n.translate("integrations.owntracks.recorder.config.saved"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.translate("integrations.owntracks.recorder.config.error", e.getMessage()));
        }

        redirectAttributes.addFlashAttribute("openSection", "external-data-stores");

        return "redirect:/settings/integrations/integrations-content?openSection=external-data-stores";
    }


    @PostMapping("/owntracks-recorder-integration/test")
    @ResponseBody
    public Map<String, Object> testOwnTracksRecorderConnection(@RequestParam String baseUrl,
                                                               @RequestParam String username,
                                                               @RequestParam String deviceId,
                                                               @RequestParam String authUsername,
                                                               @RequestParam String authPassword) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean connectionSuccessful = ownTracksRecorderIntegrationService.testConnection(baseUrl, username, authUsername, authPassword, deviceId);

            if (connectionSuccessful) {
                response.put("success", true);
                response.put("message", i18n.translate("integrations.owntracks.recorder.connection.success"));
            } else {
                response.put("success", false);
                response.put("message", i18n.translate("integrations.owntracks.recorder.connection.failed", "Invalid configuration"));
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", i18n.translate("integrations.owntracks.recorder.connection.failed", e.getMessage()));
        }

        return response;
    }

    @PostMapping("/owntracks-recorder-integration/load-historical")
    public String loadOwnTracksRecorderHistoricalData(@AuthenticationPrincipal User currentUser, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            ownTracksRecorderIntegrationService.loadHistoricalData(currentUser);
            redirectAttributes.addFlashAttribute("successMessage", i18n.translate("integrations.owntracks.recorder.load.historical.success"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.translate("integrations.owntracks.recorder.load.historical.error", e.getMessage()));
        }
        return "redirect:/settings/integrations/integrations-content?openSection=external-data-stores";
    }

    @PostMapping("/mqtt-integration")
    public String saveMqttIntegration(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "mqtt_host") String host,
            @RequestParam(name = "mqtt_port") int port,
            @RequestParam(name = "mqtt_identifier") String identifier,
            @RequestParam(name = "mqtt_topic") String topic,
            @RequestParam(name = "mqtt_username", required = false) String username,
            @RequestParam(name = "mqtt_password", required = false) String password,
            @RequestParam(name = "mqtt_payloadType") PayloadType payloadType,
            @RequestParam(name = "mqtt_enabled",defaultValue = "false") boolean enabled,
            RedirectAttributes redirectAttributes) {

        try {
            // Validate topic doesn't contain wildcard characters
            if (topic.contains("+") || topic.contains("#")) {
                redirectAttributes.addFlashAttribute("errorMessage", i18n.translate("integration.mqtt.error.wildcard"));
                return "redirect:/settings/integrations/integrations-content?openSection=mqtt";
            }

            // Validate port range
            if (port < 1 || port > 65535) {
                redirectAttributes.addFlashAttribute("errorMessage", i18n.translate("integration.mqtt.error.port_range"));
                return "redirect:/settings/integrations/integrations-content?openSection=mqtt";

            }

            MqttIntegration mqttIntegration = this.mqttIntegrationJdbcService.findByUser(user).orElse(MqttIntegration.empty());
            boolean wasEnabled = mqttIntegration.isEnabled();
            MqttIntegration updatedIntegration = mqttIntegration
                    .withHost(host)
                    .withPort(port)
                    .withIdentifier(identifier)
                    .withTopic(topic)
                    .withUsername(username)
                    .withPassword(password)
                    .withPayloadType(payloadType)
                    .withEnabled(enabled);
            mqttIntegrationJdbcService.save(user, updatedIntegration);
            if (wasEnabled && !updatedIntegration.isEnabled()) {
                this.mqttProvider.remove(user);
            }
            if (updatedIntegration.isEnabled()) {
                this.mqttProvider.register(user, updatedIntegration);
            }

            redirectAttributes.addFlashAttribute("successMessage", i18n.translate("integration.mqtt.success.saved"));

        } catch (OptimisticLockException e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.translate("integration.mqtt.error.out_of_date"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", i18n.translate("integration.mqtt.error.saving", e.getMessage()));
        }
        return "redirect:/settings/integrations/integrations-content?openSection=mqtt";
    }

    @PostMapping("/mqtt-integration/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testMqttConnection(
            @RequestParam(name = "mqtt_host") String host,
            @RequestParam(name = "mqtt_port") int port,
            @RequestParam(name = "mqtt_identifier") String identifier,
            @RequestParam(name = "mqtt_topic") String topic,
            @RequestParam(name = "mqtt_username", required = false) String username,
            @RequestParam(name = "mqtt_password", required = false) String password,
            @RequestParam(name = "mqtt_payloadType") PayloadType payloadType) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Validate basic parameters
            if (host == null || host.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", i18n.translate("integration.mqtt.error.host_required"));
                return ResponseEntity.ok(response);
            }

            if (port < 1 || port > 65535) {
                response.put("success", false);
                response.put("message", i18n.translate("integration.mqtt.error.port_range"));
                return ResponseEntity.ok(response);
            }

            if (identifier == null || identifier.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", i18n.translate("integration.mqtt.error.identifier_required"));
                return ResponseEntity.ok(response);
            }

            if (topic == null || topic.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", i18n.translate("integration.mqtt.error.topic_required"));
                return ResponseEntity.ok(response);
            }

            CompletableFuture<DynamicMqttProvider.MqttTestResult> testResult = this.mqttProvider.testConnection(new MqttIntegration(null,
                                                                                                                                    host,
                                                                                                                                    port,
                                                                                                                                    null,
                                                                                                                                    topic,
                                                                                                                                    username,
                                                                                                                                    password,
                                                                                                                                    payloadType,
                                                                                                                                    true,
                                                                                                                                    null,
                                                                                                                                    null,
                                                                                                                                    null,
                                                                                                                                    null));

            // Wait for the test result and handle it
            DynamicMqttProvider.MqttTestResult result = testResult.get(); // This might throw an exception if the test fails
            if (result.success()) {
                response.put("success", true);
                response.put("message", i18n.translate("integration.mqtt.success.test"));
            } else {
                response.put("success", false);
                response.put("message", i18n.translate("integration.mqtt.error.test_failed", result.message()));
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", i18n.translate("integration.mqtt.error.test_failed", e.getMessage()));
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/data-quality-content")
    public String getDataQualityContent(@AuthenticationPrincipal User user, Model model) {
        try {
            DataQualityReport dataQuality = generateDataQualityReport(user);
            model.addAttribute("dataQuality", dataQuality);
        } catch (Exception e) {
            model.addAttribute("errorMessage", i18n.translate("integrations.data.quality.error", e.getMessage()));
        }
        return "settings/integrations :: data-quality-content";
    }

    private DataQualityReport generateDataQualityReport(User user) {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant oneDayAgo = now.minus(24, java.time.temporal.ChronoUnit.HOURS);
        java.time.Instant sevenDaysAgo = now.minus(7, java.time.temporal.ChronoUnit.DAYS);

        // Get location points for different time periods
        List<RawLocationPoint> allPoints = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                user, sevenDaysAgo, now);

        List<RawLocationPoint> last24hPoints = allPoints.stream()
                .filter(point -> point.getTimestamp().isAfter(oneDayAgo))
                .toList();

        // Calculate basic statistics
        long totalPoints = rawLocationPointJdbcService.countByUser(user);
        int pointsLast24h = last24hPoints.size();
        int pointsLast7d = allPoints.size();
        int avgPointsPerDay = pointsLast7d > 0 ? pointsLast7d / 7 : 0;

        // Find latest point
        String latestPointTime = null;
        String timeSinceLastPoint = null;
        if (!allPoints.isEmpty()) {
            RawLocationPoint latestPoint = allPoints.getLast();
            latestPointTime = latestPoint.getTimestamp().toString();

            long minutesSince = java.time.Duration.between(latestPoint.getTimestamp(), now).toMinutes();
            if (minutesSince < 60) {
                timeSinceLastPoint = minutesSince + " minutes ago";
            } else if (minutesSince < 1440) {
                timeSinceLastPoint = (minutesSince / 60) + " hours ago";
            } else {
                timeSinceLastPoint = (minutesSince / 1440) + " days ago";
            }
        }

        // Calculate accuracy statistics
        Double avgAccuracy = null;
        Integer goodAccuracyPercentage = null;
        if (!allPoints.isEmpty()) {
            List<Double> accuracies = allPoints.stream()
                    .map(RawLocationPoint::getAccuracyMeters)
                    .filter(Objects::nonNull)
                    .toList();

            if (!accuracies.isEmpty()) {
                avgAccuracy = accuracies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                long goodAccuracyCount = accuracies.stream().filter(acc -> acc < 50.0).count();
                goodAccuracyPercentage = (int) ((goodAccuracyCount * 100) / accuracies.size());
            }
        }

        // Calculate average interval between points and check for fluctuation
        String avgInterval = null;
        long avgIntervalSeconds = -1;
        boolean hasFluctuatingFrequency = false;


        if (last24hPoints.size() > 1) {

            avgIntervalSeconds = (24 * 60 * 60) / last24hPoints.size();

            if (avgIntervalSeconds < 60) {
                avgInterval = avgIntervalSeconds + " seconds";
            } else if (avgIntervalSeconds < 3600) {
                avgInterval = (avgIntervalSeconds / 60) + " minutes";
            } else {
                avgInterval = (avgIntervalSeconds / 3600) + " hours";
            }
        }

        // Check for frequency fluctuation using only last 24h data
        if (last24hPoints.size() > 2) {
            List<Long> last24hIntervals = new ArrayList<>();
            long totalLast24hIntervalSeconds = 0;

            for (int i = 1; i < last24hPoints.size(); i++) {
                long intervalSeconds = java.time.Duration.between(
                        last24hPoints.get(i-1).getTimestamp(),
                        last24hPoints.get(i).getTimestamp()
                ).getSeconds();
                last24hIntervals.add(intervalSeconds);
                totalLast24hIntervalSeconds += intervalSeconds;
            }

            if (!last24hIntervals.isEmpty()) {
                long avgLast24hIntervalSeconds = totalLast24hIntervalSeconds / last24hIntervals.size();

                // Check for frequency fluctuation (coefficient of variation > 1.0)
                if (last24hIntervals.size() > 2) {
                    double variance = last24hIntervals.stream()
                            .mapToDouble(interval -> Math.pow(interval - avgLast24hIntervalSeconds, 2))
                            .average().orElse(0.0);
                    double stdDev = Math.sqrt(variance);
                    double coefficientOfVariation = avgLast24hIntervalSeconds > 0 ? stdDev / avgLast24hIntervalSeconds : 0;
                    hasFluctuatingFrequency = coefficientOfVariation > 10.0;
                }
            }
        }

        // Determine status flags
        boolean isActivelyTracking = pointsLast24h > 0;
        int recommendedFrequency = 75;
        boolean hasGoodFrequency = avgIntervalSeconds < recommendedFrequency;

        // Generate recommendations
        List<String> recommendations = new ArrayList<>();
        if (!isActivelyTracking) {
            recommendations.add(i18n.translate("integrations.data.quality.recommendation.no.data"));
        }
        if (avgIntervalSeconds > recommendedFrequency) {
            recommendations.add(i18n.translate("integrations.data.quality.recommendation.low.frequency"));
        }
        if (goodAccuracyPercentage != null && goodAccuracyPercentage < 70) {
            recommendations.add(i18n.translate("integrations.data.quality.recommendation.poor.accuracy"));
        }
        if (avgAccuracy != null && avgAccuracy > 100) {
            recommendations.add(i18n.translate("integrations.data.quality.recommendation.very.poor.accuracy"));
        }
        if (hasFluctuatingFrequency) {
            recommendations.add(i18n.translate("integrations.data.quality.recommendation.fluctuating.frequency"));
        }

        return new DataQualityReport(
                totalPoints, pointsLast24h, pointsLast7d, avgPointsPerDay,
                latestPointTime, timeSinceLastPoint,
                avgAccuracy, goodAccuracyPercentage, avgInterval,
                isActivelyTracking, hasGoodFrequency, hasFluctuatingFrequency, recommendations
        );
    }

    // Data class for the quality report
    public static class DataQualityReport {
        private final long totalPoints;
        private final int pointsLast24h;
        private final int pointsLast7d;
        private final int avgPointsPerDay;
        private final String latestPointTime;
        private final String timeSinceLastPoint;
        private final Double avgAccuracy;
        private final Integer goodAccuracyPercentage;
        private final String avgInterval;
        private final boolean isActivelyTracking;
        private final boolean hasGoodFrequency;
        private final boolean hasFluctuatingFrequency;
        private final List<String> recommendations;

        public DataQualityReport(long totalPoints, int pointsLast24h, int pointsLast7d, int avgPointsPerDay,
                                 String latestPointTime, String timeSinceLastPoint, Double avgAccuracy,
                                 Integer goodAccuracyPercentage, String avgInterval, boolean isActivelyTracking,
                                 boolean hasGoodFrequency, boolean hasFluctuatingFrequency, List<String> recommendations) {
            this.totalPoints = totalPoints;
            this.pointsLast24h = pointsLast24h;
            this.pointsLast7d = pointsLast7d;
            this.avgPointsPerDay = avgPointsPerDay;
            this.latestPointTime = latestPointTime;
            this.timeSinceLastPoint = timeSinceLastPoint;
            this.avgAccuracy = avgAccuracy;
            this.goodAccuracyPercentage = goodAccuracyPercentage;
            this.avgInterval = avgInterval;
            this.isActivelyTracking = isActivelyTracking;
            this.hasGoodFrequency = hasGoodFrequency;
            this.hasFluctuatingFrequency = hasFluctuatingFrequency;
            this.recommendations = recommendations;
        }

        // Getters
        public long getTotalPoints() { return totalPoints; }
        public int getPointsLast24h() { return pointsLast24h; }
        public int getPointsLast7d() { return pointsLast7d; }
        public int getAvgPointsPerDay() { return avgPointsPerDay; }
        public String getLatestPointTime() { return latestPointTime; }
        public String getTimeSinceLastPoint() { return timeSinceLastPoint; }
        public Double getAvgAccuracy() { return avgAccuracy; }
        public Integer getGoodAccuracyPercentage() { return goodAccuracyPercentage; }
        public String getAvgInterval() { return avgInterval; }
        public boolean isActivelyTracking() { return isActivelyTracking; }
        public boolean isHasGoodFrequency() { return hasGoodFrequency; }
        public boolean isHasFluctuatingFrequency() { return hasFluctuatingFrequency; }
        public List<String> getRecommendations() { return recommendations; }
    }
}
