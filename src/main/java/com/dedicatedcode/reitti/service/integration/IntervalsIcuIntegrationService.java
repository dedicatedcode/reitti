package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.model.IntegrationTestResult;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.integration.IntervalsIcuIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.IntervalsIcuImportedActivityJdbcService;
import com.dedicatedcode.reitti.repository.IntervalsIcuIntegrationJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.VersionService;
import com.dedicatedcode.reitti.service.importer.FitFileImporter;
import com.dedicatedcode.reitti.service.importer.GpxImporter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Service
public class IntervalsIcuIntegrationService {

    private static final String BASE_URL = "https://intervals.icu";

    private static final Logger logger = LoggerFactory.getLogger(IntervalsIcuIntegrationService.class);

    private static final String AUTH_USERNAME = "API_KEY";
    private static final String API_PATH = "/api/v1";
    private static final int ACTIVITY_PAGE_LIMIT = 100;
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final TypeReference<List<ActivitySummary>> ACTIVITY_LIST_TYPE = new TypeReference<>() {
    };
    /**
     * Asks the activities endpoint for a projection. Without it every activity arrives as a ~200 field
     * document of which 99 percent is unused, and the server also strips null values from a projection,
     * which keeps the response small as well as safe to bind.
     */
    private static final String ACTIVITY_FIELDS = "id,file_type,start_date_local";

    private final IntervalsIcuIntegrationJdbcService jdbcService;
    private final IntervalsIcuImportedActivityJdbcService importedActivityService;
    private final UserJdbcService userJdbcService;
    private final DeviceJdbcService deviceJdbcService;
    private final FitFileImporter fitFileImporter;
    private final GpxImporter gpxImporter;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String userAgent;
    private final int maxActivitiesPerRun;
    private final LocalDate historyEarliestDate;
    private final Duration sliceDelay;

    public IntervalsIcuIntegrationService(IntervalsIcuIntegrationJdbcService jdbcService,
                                          IntervalsIcuImportedActivityJdbcService importedActivityService,
                                          UserJdbcService userJdbcService,
                                          DeviceJdbcService deviceJdbcService,
                                          FitFileImporter fitFileImporter,
                                          GpxImporter gpxImporter,
                                          RestTemplate restTemplate,
                                          ObjectMapper objectMapper,
                                          VersionService versionService,
                                          @Value("${reitti.imports.intervals-icu.max-activities-per-run:25}") int maxActivitiesPerRun,
                                          @Value("${reitti.imports.intervals-icu.history-earliest-date:2010-01-01}") LocalDate historyEarliestDate,
                                          @Value("${reitti.imports.intervals-icu.slice-delay-seconds:2}") long sliceDelaySeconds) {
        this.jdbcService = jdbcService;
        this.importedActivityService = importedActivityService;
        this.userJdbcService = userJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.fitFileImporter = fitFileImporter;
        this.gpxImporter = gpxImporter;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.maxActivitiesPerRun = maxActivitiesPerRun;
        this.historyEarliestDate = historyEarliestDate;
        this.sliceDelay = Duration.ofSeconds(sliceDelaySeconds);
        this.userAgent = "Reitti/%s (+https://github.com/dedicatedcode/reitti)".formatted(versionService.getVersion());
    }

    @Scheduled(cron = "${reitti.imports.intervals-icu.schedule}")
    void importNewData() {
        List<User> allUsers = userJdbcService.findAll();
        int processedIntegrations = 0;

        for (User user : allUsers) {
            Optional<IntervalsIcuIntegration> integrationOpt = jdbcService.findByUser(user);
            if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
                continue;
            }
            processedIntegrations++;
            try {
                SyncResult result = syncIncremental(user);
                if (result.imported() > 0) {
                    logger.info("Imported {} intervals.icu activities for user {}", result.imported(), user.getUsername());
                }
            } catch (Exception e) {
                logger.error("Failed to import data for user {} from intervals.icu: {}", user.getUsername(), e.getMessage(), e);
            }
        }

        logger.trace("intervals.icu import completed: processed {} integrations", processedIntegrations);
    }

    public Optional<IntervalsIcuIntegration> getIntegrationForUser(User user) {
        return jdbcService.findByUser(user);
    }

    public IntervalsIcuIntegration saveIntegration(User user, Device device, String apiKey, boolean enabled) {
        String normalizedApiKey = apiKey == null ? "" : apiKey.trim();
        if (normalizedApiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be empty");
        }

        Optional<IntervalsIcuIntegration> existing = jdbcService.findByUser(user);
        IntervalsIcuIntegration candidate = existing
                .map(current -> new IntervalsIcuIntegration(current.getId(), normalizedApiKey, device.id(),
                        current.getAthleteId(), current.getAthleteName(), enabled,
                        current.getLastSuccessfulFetch(), current.getVersion()))
                .orElseGet(() -> new IntervalsIcuIntegration(normalizedApiKey, device.id(), enabled));

        return existing.isPresent() ? jdbcService.update(user, candidate) : jdbcService.save(user, candidate);
    }

    public IntegrationTestResult testConnection(String apiKey) {
        IntervalsIcuIntegration probe = new IntervalsIcuIntegration(apiKey == null ? "" : apiKey.trim(), 0L, true);
        try {
            AthleteInfo athlete = fetchAthlete(probe);
            return new IntegrationTestResult(true, athlete.name());
        } catch (RestClientResponseException e) {
            logger.debug("intervals.icu connection test failed with status {}", e.getStatusCode());
            return IntegrationTestResult.failed("HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            return IntegrationTestResult.failed(e.getMessage());
        }
    }

    public SyncResult syncIncremental(User user) {
        IntervalsIcuIntegration integration = requireIntegration(user);
        LocalDate newest = LocalDate.now(ZoneOffset.UTC);
        LocalDate oldest = integration.getLastSuccessfulFetch() == null
                ? newest
                : integration.getLastSuccessfulFetch().atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
        if (oldest.isAfter(newest)) {
            oldest = newest;
        }
        return syncRange(user, oldest, newest, true);
    }

    public LocalDate getHistoryEarliestDate() {
        return historyEarliestDate;
    }

    public Duration getSliceDelay() {
        return sliceDelay;
    }

    public SyncResult importHistoricalSlice(User user, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both a start and an end date are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The start date must not be after the end date");
        }
        return syncRange(user, from, to, false);
    }

    private SyncResult syncRange(User user, LocalDate from, LocalDate to, boolean advanceWatermark) {
        IntervalsIcuIntegration integration = refreshAthleteIfUnknown(user, requireIntegration(user));
        Device device = this.deviceJdbcService.find(user, integration.getReittiDeviceId())
                .orElseThrow(() -> new IllegalStateException("Configured Reitti device no longer exists"));

        Window window = fetchActivityWindow(integration, from, to);
        Set<String> alreadyImported = importedActivityService.findImportedIds(
                user, window.activities().stream().map(ActivitySummary::id).filter(Objects::nonNull).toList());

        List<ActivitySummary> pending = new ArrayList<>();
        for (ActivitySummary activity : window.activities()) {
            if (activity.id() == null || activity.id().isBlank() || alreadyImported.contains(activity.id())) {
                continue;
            }
            pending.add(activity);
        }

        boolean truncated = window.truncated() || (maxActivitiesPerRun > 0 && pending.size() > maxActivitiesPerRun);
        List<ActivitySummary> selected = maxActivitiesPerRun > 0 && pending.size() > maxActivitiesPerRun
                ? pending.subList(0, maxActivitiesPerRun)
                : pending;

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        for (ActivitySummary activity : selected) {
            try {
                ImportOutcome outcome = importActivity(user, device, integration, activity);
                // Mark the activity as processed even when it yielded no points. Otherwise activities
                // without a track (indoor trainer rides, swims, weight training) would be re-downloaded on
                // every single run. One wasted request per trackless activity, once, is the only cost.
                importedActivityService.markImported(user, activity.id(), activity.startDateLocal(), outcome.pointsReceived());
                if (outcome.pointsReceived() > 0) {
                    imported++;
                } else {
                    logger.warn("intervals.icu activity {} contained no location points", activity.id());
                    skipped++;
                }
            } catch (Exception e) {
                // Leave the activity unmarked so the next run retries it.
                logger.error("Failed to import intervals.icu activity {}: {}", activity.id(), e.getMessage(), e);
                failed++;
            }
        }

        if (advanceWatermark && !truncated) {
            jdbcService.update(user, integration.withLastSuccessfulFetch(to.atTime(23, 59, 59).toInstant(ZoneOffset.UTC)));
        }

        int remaining = pending.size() - selected.size();
        LocalDate resumeFrom = null;
        if (remaining > 0 && !selected.isEmpty()) {
            // Walk back to the newest activity in this slice that carries a date. An activity without
            // start_date_local still got processed, it just cannot be used to position the next window.
            for (int i = selected.size() - 1; i >= 0 && resumeFrom == null; i--) {
                LocalDateTime start = selected.get(i).startDateLocal();
                if (start != null) {
                    resumeFrom = start.toLocalDate();
                }
            }
            if (resumeFrom == null) {
                logger.warn("None of the {} processed intervals.icu activities carries a start date, "
                        + "stopping the historical import at {}", selected.size(), from);
            }
        }

        logger.debug("intervals.icu sync for user {}: {} activities in window, {} imported, {} skipped, {} failed, {} remaining",
                user.getUsername(), window.activities().size(), imported, skipped, failed, remaining);

        return new SyncResult(window.activities().size(), imported, skipped, failed, remaining, resumeFrom);
    }

    private ImportOutcome importActivity(User user, Device device, IntervalsIcuIntegration integration, ActivitySummary activity) {
        String fileType = activity.fileType() == null ? "" : activity.fileType().toLowerCase(Locale.ROOT);
        boolean importable = fileType.equals("fit") || fileType.equals("gpx");
        String extension = importable ? fileType : "fit";

        byte[] payload = download(integration, "/activity/" + activity.id() + (importable ? "/file" : "/fit-file"));
        byte[] content = decompressIfGzipped(payload);
        String fileName = "intervals-icu-%s.%s".formatted(activity.id(), extension);

        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            Map<String, Object> result = fileType.equals("gpx")
                    ? gpxImporter.importGpx(inputStream, user, device, fileName)
                    : fitFileImporter.importFile(inputStream, user, device, fileName);
            return new ImportOutcome(readPointsReceived(result));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long readPointsReceived(Map<String, Object> result) {
        Object value = result == null ? null : result.get("pointsReceived");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * Reads the whole window oldest first. The activities endpoint returns newest first and honours a limit,
     * so large windows are walked backwards by narrowing the newest date until a short page is returned.
     */
    private Window fetchActivityWindow(IntervalsIcuIntegration integration, LocalDate from, LocalDate to) {
        List<ActivitySummary> collected = new ArrayList<>();
        boolean truncated = false;
        LocalDate currentTo = to;

        while (true) {
            List<ActivitySummary> page = fetchActivities(integration, from, currentTo);
            if (page.isEmpty()) {
                break;
            }
            collected.addAll(page);

            if (page.size() < ACTIVITY_PAGE_LIMIT) {
                break;
            }
            truncated = true;

            ActivitySummary oldestOnPage = page.getLast();
            LocalDate oldestDate = oldestOnPage.startDateLocal() != null
                    ? oldestOnPage.startDateLocal().toLocalDate()
                    : null;
            if (oldestDate == null || !oldestDate.isBefore(currentTo)) {
                break;
            }
            currentTo = oldestDate.minusDays(1);
            if (currentTo.isBefore(from)) {
                break;
            }
        }

        Collections.reverse(collected);
        return new Window(collected, truncated);
    }

    private List<ActivitySummary> fetchActivities(IntervalsIcuIntegration integration, LocalDate from, LocalDate to) {
        String url = ("%s%s/athlete/0/activities?oldest=%s&newest=%s&limit=%d&fields=%s"
                .formatted(BASE_URL, API_PATH, from, to, ACTIVITY_PAGE_LIMIT, ACTIVITY_FIELDS));
        logger.debug("Fetching intervals.icu activities: {}", url);

        String body = get(url, integration.getApiKey(), MediaType.APPLICATION_JSON);
        try {
            List<ActivitySummary> activities = this.objectMapper.readValue(body, ACTIVITY_LIST_TYPE);
            return activities != null ? activities : List.of();
        } catch (JacksonException e) {
            throw unexpectedResponse(url, body, e);
        }
    }

    /**
     * Reads the body as text and leaves the parsing to the caller. Asking RestTemplate for a typed response
     * instead breaks on error responses: intervals.icu answers a rejected key with a JSON object such as
     * {"status":401,"error":"Unauthorized"}, and the error handler then tries to read that object into the
     * expected list type, which surfaces as an opaque "Error while extracting response" instead of the
     * actual 401.
     */
    private String get(String url, String apiKey, MediaType accept) {
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(createHeaders(apiKey, accept)), String.class);
        return response.getBody() != null ? response.getBody() : "";
    }

    private IllegalStateException unexpectedResponse(String url, String body, Exception cause) {
        String excerpt = body.length() > 200 ? body.substring(0, 200) + "..." : body;
        logger.warn("Unexpected response from {}: {}", url, excerpt);
        return new IllegalStateException("Unexpected response from " + url + ": " + excerpt, cause);
    }

    /**
     * Resolves the linked intervals.icu account once so the settings page can show who is connected.
     * Only attempted while the identity is still unknown, so it costs at most one extra request per sync.
     */
    private IntervalsIcuIntegration refreshAthleteIfUnknown(User user, IntervalsIcuIntegration integration) {
        if (integration.getAthleteName() != null) {
            return integration;
        }
        try {
            AthleteInfo athlete = fetchAthlete(integration);
            if (athlete.name() == null && athlete.athleteId() == null) {
                return integration;
            }
            return jdbcService.update(user, integration.withAthlete(athlete.athleteId(), athlete.name()));
        } catch (Exception e) {
            logger.debug("Could not resolve the intervals.icu athlete for user {}: {}", user.getUsername(), e.getMessage());
            return integration;
        }
    }

    private AthleteInfo fetchAthlete(IntervalsIcuIntegration integration) {
        String url = BASE_URL + API_PATH + "/athlete/0";
        String body = get(url, integration.getApiKey(), MediaType.APPLICATION_JSON);
        try {
            AthleteInfo athlete = this.objectMapper.readValue(body, AthleteInfo.class);
            return athlete != null ? athlete : new AthleteInfo(null, null);
        } catch (JacksonException e) {
            throw unexpectedResponse(url, body, e);
        }
    }

    private byte[] download(IntervalsIcuIntegration integration, String path) {
        String url = BASE_URL + API_PATH + path;
        logger.debug("Downloading intervals.icu activity file: {}", url);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(createHeaders(integration.getApiKey(), MediaType.ALL)),
                byte[].class);

        byte[] payload = response.getBody();
        if (payload == null || payload.length == 0) {
            throw new IllegalStateException("intervals.icu returned an empty file for " + path);
        }
        return payload;
    }

    private byte[] decompressIfGzipped(byte[] payload) {
        if (payload.length < 2 || (payload[0] & 0xff) != 0x1f || (payload[1] & 0xff) != 0x8b) {
            return payload;
        }
        try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(payload));
             ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length * 4)) {
            gzip.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decompress the activity file downloaded from intervals.icu", e);
        }
    }

    private HttpHeaders createHeaders(String apiKey, MediaType accept) {
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.isBlank()) {
            String credentials = AUTH_USERNAME + ":" + apiKey;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        headers.setAccept(List.of(accept));
        return headers;
    }

    private IntervalsIcuIntegration requireIntegration(User user) {
        return jdbcService.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("No intervals.icu integration found for user"));
    }

    public record SyncResult(int total, int imported, int skipped, int failed, int remaining, LocalDate resumeFrom) {
    }

    private record ImportOutcome(long pointsReceived) {
    }

    private record Window(List<ActivitySummary> activities, boolean truncated) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AthleteInfo(@JsonProperty("id") String athleteId, @JsonProperty("name") String name) {
    }

    /**
     * The only three values the import needs: the id used to download the file and to deduplicate, the
     * original file type used to pick an importer, and the start date used to resume a historical import.
     *
     * Every field is a String on purpose. The activities endpoint is asked for exactly these fields, but
     * should it ever answer with the full object instead, this still binds cleanly: intervals.icu omits or
     * nulls a large share of its activity properties, and Jackson 3 rejects null for a primitive field. A
     * primitive boolean trainer flag used to abort the whole sync on the first uploaded activity.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ActivitySummary {

        @JsonProperty("id")
        private String id;

        @JsonProperty("file_type")
        private String fileType;

        @JsonProperty("start_date_local")
        private String startDateLocal;

        String id() {
            return id;
        }

        String fileType() {
            return fileType;
        }

        LocalDateTime startDateLocal() {
            if (startDateLocal == null || startDateLocal.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(startDateLocal, LOCAL_DATE_TIME);
            } catch (Exception e) {
                logger.debug("Unparseable intervals.icu start_date_local: {}", startDateLocal);
                return null;
            }
        }
    }
}
