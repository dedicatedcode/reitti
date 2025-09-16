package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.IntegrationTestResult;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.integration.OwnTracksRecorderIntegration;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.ApiTokenService;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import com.dedicatedcode.reitti.service.integration.OwnTracksRecorderIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/settings/integrations")
public class IntegrationsSettingsController {
    private final ApiTokenService apiTokenService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final ImmichIntegrationService immichIntegrationService;
    private final OwnTracksRecorderIntegrationService ownTracksRecorderIntegrationService;
    private final MessageSource messageSource;
    private final boolean dataManagementEnabled;

    public IntegrationsSettingsController(ApiTokenService apiTokenService, RawLocationPointJdbcService rawLocationPointJdbcService, ImmichIntegrationService immichIntegrationService,
                                          OwnTracksRecorderIntegrationService ownTracksRecorderIntegrationService,
                                          MessageSource messageSource,
                                          @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled) {
        this.apiTokenService = apiTokenService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.immichIntegrationService = immichIntegrationService;
        this.ownTracksRecorderIntegrationService = ownTracksRecorderIntegrationService;
        this.messageSource = messageSource;
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
        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
            model.addAttribute("hasToken", true);
        } else {
            model.addAttribute("hasToken", false);
        }

        Optional<OwnTracksRecorderIntegration> recorderIntegration = ownTracksRecorderIntegrationService.getIntegrationForUser(user);
        if (recorderIntegration.isPresent()) {
            model.addAttribute("ownTracksRecorderIntegration", recorderIntegration.get());
            model.addAttribute("hasRecorderIntegration", recorderIntegration.get().isEnabled());
        } else {
            model.addAttribute("hasRecorderIntegration", false);
        }

        model.addAttribute("openSection", openSection);
        model.addAttribute("serverUrl", calculateServerUrl(request));

        return "settings/integrations";
    }

    @GetMapping("/integrations-content")
    public String getIntegrationsContent(@AuthenticationPrincipal User currentUser,
                                         HttpServletRequest request,
                                         Model model,
                                         @RequestParam(required = false) String openSection) {
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);

        // Add the first token if available
        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
            model.addAttribute("hasToken", true);
        } else {
            model.addAttribute("hasToken", false);
        }

        Optional<OwnTracksRecorderIntegration> recorderIntegration = ownTracksRecorderIntegrationService.getIntegrationForUser(currentUser);
        if (recorderIntegration.isPresent()) {
            model.addAttribute("ownTracksRecorderIntegration", recorderIntegration.get());
            model.addAttribute("hasRecorderIntegration", recorderIntegration.get().isEnabled());
        } else {
            model.addAttribute("hasRecorderIntegration", false);
        }

        model.addAttribute("openSection", openSection);
        model.addAttribute("serverUrl", calculateServerUrl(request));

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
            model.addAttribute("successMessage", getMessage("integrations.immich.config.saved"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.immich.config.error", e.getMessage()));
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
                response.put("message", getMessage("integrations.immich.connection.success"));
            } else {
                response.put("success", false);
                response.put("message", getMessage("integrations.immich.connection.failed", result.message()));
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", getMessage("integrations.immich.connection.failed", e.getMessage()));
        }

        return response;
    }


    @PostMapping("/owntracks-recorder-integration")
    public String saveOwnTracksRecorderIntegration(@RequestParam String baseUrl,
                                                   @RequestParam String username,
                                                   @RequestParam String deviceId,
                                                   @RequestParam(defaultValue = "false") boolean enabled,
                                                   @AuthenticationPrincipal User currentUser,
                                                   Model model) {

        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);

        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
            model.addAttribute("hasToken", true);
        } else {
            model.addAttribute("hasToken", false);
        }

        try {
            OwnTracksRecorderIntegration integration = ownTracksRecorderIntegrationService.saveIntegration(
                    currentUser, baseUrl, username, deviceId, enabled);

            model.addAttribute("successMessage", getMessage("integrations.owntracks.recorder.config.saved"));
            model.addAttribute("ownTracksRecorderIntegration", integration);
            model.addAttribute("hasRecorderIntegration", enabled);
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.owntracks.recorder.config.error", e.getMessage()));

            // Re-populate form with submitted values for error case
            OwnTracksRecorderIntegration tempIntegration = new OwnTracksRecorderIntegration(baseUrl, username, deviceId, enabled);
            model.addAttribute("ownTracksRecorderIntegration", tempIntegration);
            model.addAttribute("hasRecorderIntegration", enabled);
        }

        // Keep external data stores section open
        model.addAttribute("openSection", "external-data-stores");

        return "settings/integrations :: integrations-content";
    }


    @PostMapping("/owntracks-recorder-integration/test")
    @ResponseBody
    public Map<String, Object> testOwnTracksRecorderConnection(@RequestParam String baseUrl,
                                                               @RequestParam String username,
                                                               @RequestParam String deviceId) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean connectionSuccessful = ownTracksRecorderIntegrationService.testConnection(baseUrl, username, deviceId);

            if (connectionSuccessful) {
                response.put("success", true);
                response.put("message", getMessage("integrations.owntracks.recorder.connection.success"));
            } else {
                response.put("success", false);
                response.put("message", getMessage("integrations.owntracks.recorder.connection.failed", "Invalid configuration"));
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", getMessage("integrations.owntracks.recorder.connection.failed", e.getMessage()));
        }

        return response;
    }

    @PostMapping("/owntracks-recorder-integration/delete")
    public String deleteOwnTracksRecorderIntegration(@AuthenticationPrincipal User currentUser, Model model, HttpServletRequest request) {
        try {
            ownTracksRecorderIntegrationService.deleteIntegration(currentUser);
            model.addAttribute("successMessage", getMessage("integrations.owntracks.recorder.config.deleted"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.owntracks.recorder.config.delete.error", e.getMessage()));
        }

        // Re-populate the integrations content
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);
        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
            model.addAttribute("hasToken", true);
        } else {
            model.addAttribute("hasToken", false);
        }

        // Build the server URL

        model.addAttribute("serverUrl", calculateServerUrl(request));
        model.addAttribute("hasRecorderIntegration", false);

        return "settings/integrations :: integrations-content";
    }

    @PostMapping("/owntracks-recorder-integration/load-historical")
    public String loadOwnTracksRecorderHistoricalData(@AuthenticationPrincipal User currentUser, Model model, HttpServletRequest request) {
        try {
            ownTracksRecorderIntegrationService.loadHistoricalData(currentUser);
            model.addAttribute("successMessage", getMessage("integrations.owntracks.recorder.load.historical.success"));
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.owntracks.recorder.load.historical.error", e.getMessage()));
        }

        // Re-populate the integrations content
        List<ApiToken> tokens = apiTokenService.getTokensForUser(currentUser);
        if (!tokens.isEmpty()) {
            model.addAttribute("firstToken", tokens.getFirst().getToken());
            model.addAttribute("hasToken", true);
        } else {
            model.addAttribute("hasToken", false);
        }
        model.addAttribute("serverUrl", calculateServerUrl(request));

        Optional<OwnTracksRecorderIntegration> recorderIntegration = ownTracksRecorderIntegrationService.getIntegrationForUser(currentUser);
        if (recorderIntegration.isPresent()) {
            model.addAttribute("ownTracksRecorderIntegration", recorderIntegration.get());
            model.addAttribute("hasRecorderIntegration", recorderIntegration.get().isEnabled());
        } else {
            model.addAttribute("hasRecorderIntegration", false);
        }

        // Keep external data stores section open
        model.addAttribute("openSection", "external-data-stores");

        return "settings/integrations :: integrations-content";
    }


    @GetMapping("/data-quality-content")
    public String getDataQualityContent(@AuthenticationPrincipal User user, Model model) {
        try {
            DataQualityReport dataQuality = generateDataQualityReport(user);
            model.addAttribute("dataQuality", dataQuality);
        } catch (Exception e) {
            model.addAttribute("errorMessage", getMessage("integrations.data.quality.error", e.getMessage()));
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

        if (allPoints.size() > 1) {
            List<Long> intervals = new ArrayList<>();
            long totalIntervalSeconds = 0;

            for (int i = 1; i < allPoints.size(); i++) {
                long intervalSeconds = java.time.Duration.between(
                        allPoints.get(i-1).getTimestamp(),
                        allPoints.get(i).getTimestamp()
                ).getSeconds();
                intervals.add(intervalSeconds);
                totalIntervalSeconds += intervalSeconds;
            }

            avgIntervalSeconds = totalIntervalSeconds / intervals.size();

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
                    long finalAvgLast24hIntervalSeconds = avgLast24hIntervalSeconds;
                    double variance = last24hIntervals.stream()
                            .mapToDouble(interval -> Math.pow(interval - finalAvgLast24hIntervalSeconds, 2))
                            .average().orElse(0.0);
                    double stdDev = Math.sqrt(variance);
                    double coefficientOfVariation = avgLast24hIntervalSeconds > 0 ? stdDev / avgLast24hIntervalSeconds : 0;
                    hasFluctuatingFrequency = coefficientOfVariation > 1.0;
                }
            }
        }

        // Determine status flags
        boolean isActivelyTracking = pointsLast24h > 0;
        boolean hasGoodFrequency = avgIntervalSeconds < 50;

        // Generate recommendations
        List<String> recommendations = new ArrayList<>();
        if (!isActivelyTracking) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.no.data"));
        }
        if (avgIntervalSeconds > 50) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.low.frequency"));
        }
        if (goodAccuracyPercentage != null && goodAccuracyPercentage < 70) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.poor.accuracy"));
        }
        if (avgAccuracy != null && avgAccuracy > 100) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.very.poor.accuracy"));
        }
        if (hasFluctuatingFrequency) {
            recommendations.add(getMessage("integrations.data.quality.recommendation.fluctuating.frequency"));
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

    private String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

}
