package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.IntegrationTestResult;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.integration.IntervalsIcuIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.IntervalsIcuImportedActivityJdbcService;
import com.dedicatedcode.reitti.repository.IntervalsIcuIntegrationJdbcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

@IntegrationTest
class IntervalsIcuIntegrationServiceTest {

    private static final String BASE_URL = "https://intervals.icu";
    private static final String API_KEY = "test-api-key";
    private static final LocalDate FROM = LocalDate.parse("2024-11-01");
    private static final LocalDate TO = LocalDate.parse("2024-11-30");

    private static final String TRACKLESS_GPX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
                <trk><name>Indoor Trainer Ride</name><trkseg></trkseg></trk>
            </gpx>
            """;

    @Autowired
    private IntervalsIcuIntegrationService service;

    @Autowired
    private IntervalsIcuIntegrationJdbcService integrationJdbcService;

    @Autowired
    private IntervalsIcuImportedActivityJdbcService importedActivityService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockRestServiceServer mockServer;
    private ClientHttpRequestFactory originalRequestFactory;
    private User user;
    private Device device;

    @BeforeEach
    void setUp() {
        originalRequestFactory = restTemplate.getRequestFactory();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        user = testingService.randomUser();
        device = testingService.findDefaultDevice(user);
        // The linked athlete is seeded so the sync does not spend an extra request resolving it.
        integrationJdbcService.save(user, new IntervalsIcuIntegration(API_KEY, device.id(), true).withAthlete("i1", "Test Athlete"));
    }

    @AfterEach
    void tearDown() {
        restTemplate.setRequestFactory(originalRequestFactory);
        testingService.clearData();
    }

    @Test
    void importHistoricalSlice_ImportsOriginalFitFileAndRecordsActivity() {
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activitiesJson(
                activityJson("i1", "Morning Ride", "2024-11-19T07:35:18", "fit")),
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(downloadUrl("i1", "/file")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic QVBJX0tFWTp0ZXN0LWFwaS1rZXk="))
                .andExpect(header("User-Agent", org.hamcrest.Matchers.startsWith("Reitti/")))
                .andRespond(withSuccess(gzip("/data/fit/sample.fit"), MediaType.APPLICATION_OCTET_STREAM));

        IntervalsIcuIntegrationService.SyncResult result = service.importHistoricalSlice(user, FROM, TO);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.remaining()).isZero();
        assertThat(result.resumeFrom()).isNull();
        assertThat(importedActivityService.findImportedIds(user, List.of("i1"))).containsExactly("i1");
        assertThat(pointsReceived("i1")).isPositive();
        // A historical import must never move the incremental watermark.
        assertThat(integrationJdbcService.findByUser(user).orElseThrow().getLastSuccessfulFetch()).isNull();
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_SecondRunSkipsAlreadyImportedActivities() {
        String activities = activitiesJson(activityJson("i1", "Morning Ride", "2024-11-19T07:35:18", "fit"));
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activities, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(downloadUrl("i1", "/file")))
                .andRespond(withSuccess(gzip("/data/fit/sample.fit"), MediaType.APPLICATION_OCTET_STREAM));

        IntervalsIcuIntegrationService.SyncResult first = service.importHistoricalSlice(user, FROM, TO);
        assertThat(first.imported()).isEqualTo(1);
        mockServer.verify();

        // Second run must not download the activity again.
        mockServer.reset();
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activities, MediaType.APPLICATION_JSON));

        IntervalsIcuIntegrationService.SyncResult second = service.importHistoricalSlice(user, FROM, TO);

        assertThat(second.total()).isEqualTo(1);
        assertThat(second.imported()).isZero();
        assertThat(second.skipped()).isZero();
        assertThat(second.failed()).isZero();
        assertThat(pointsReceived("i1")).isPositive();
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_DownloadsATracklessActivityOnceAndNeverRetriesIt() {
        // Nothing is pre-filtered on the server, so an activity without a track is still downloaded. It yields
        // no points, is recorded as processed and must never be fetched again.
        String activities = activitiesJson(activityJson("i1", "Indoor Trainer Ride", "2024-11-19T18:00:00", "gpx"));
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activities, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(downloadUrl("i1", "/file")))
                .andRespond(withSuccess(gzipText(TRACKLESS_GPX), MediaType.APPLICATION_OCTET_STREAM));

        IntervalsIcuIntegrationService.SyncResult first = service.importHistoricalSlice(user, FROM, TO);

        assertThat(first.total()).isEqualTo(1);
        assertThat(first.skipped()).isEqualTo(1);
        assertThat(first.imported()).isZero();
        assertThat(importedActivityService.findImportedIds(user, List.of("i1"))).containsExactly("i1");
        assertThat(pointsReceived("i1")).isZero();
        mockServer.verify();

        mockServer.reset();
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activities, MediaType.APPLICATION_JSON));

        IntervalsIcuIntegrationService.SyncResult second = service.importHistoricalSlice(user, FROM, TO);

        assertThat(second.total()).isEqualTo(1);
        assertThat(second.imported()).isZero();
        assertThat(second.skipped()).isZero();
        // A strict ordered expectation list proves no second download was issued.
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_BindsAnUnprojectedActivityPayloadFullOfNulls() {
        // intervals.icu omits or nulls a large share of its activity properties, trainer and name among
        // them, and the full object carries around 150 of them. Binding a primitive boolean trainer flag
        // aborted the entire sync on the first uploaded activity, so the payload is exercised unprojected.
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activitiesJson(
                activityJson("i1", null, "2024-07-14T11:39:16", "fit")),
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(downloadUrl("i1", "/file")))
                .andRespond(withSuccess(gzip("/data/fit/sample.fit"), MediaType.APPLICATION_OCTET_STREAM));

        IntervalsIcuIntegrationService.SyncResult result = service.importHistoricalSlice(user, FROM, TO);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(pointsReceived("i1")).isPositive();
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_FallsBackToGeneratedFitForUnknownFileTypes() {
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activitiesJson(
                activityJson("i1", "Pool Swim", "2024-11-19T06:00:00", "tcx")),
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(downloadUrl("i1", "/fit-file")))
                .andRespond(withSuccess(gzip("/data/fit/sample.fit"), MediaType.APPLICATION_OCTET_STREAM));

        IntervalsIcuIntegrationService.SyncResult result = service.importHistoricalSlice(user, FROM, TO);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(pointsReceived("i1")).isPositive();
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_RoutesGpxActivitiesToTheGpxImporter() {
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activitiesJson(
                activityJson("i1", "Morning Run", "2024-11-19T07:35:18", "gpx")),
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(downloadUrl("i1", "/file")))
                .andRespond(withSuccess(gzip("/data/gpx/20250617.gpx"), MediaType.APPLICATION_OCTET_STREAM));

        IntervalsIcuIntegrationService.SyncResult result = service.importHistoricalSlice(user, FROM, TO);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(pointsReceived("i1")).isPositive();
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_AcceptsUncompressedFiles() {
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activitiesJson(
                activityJson("i1", "Morning Ride", "2024-11-19T07:35:18", "fit")),
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(downloadUrl("i1", "/file")))
                .andRespond(withSuccess(resource("/data/fit/sample.fit"), MediaType.APPLICATION_OCTET_STREAM));

        IntervalsIcuIntegrationService.SyncResult result = service.importHistoricalSlice(user, FROM, TO);

        assertThat(result.imported()).isEqualTo(1);
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_LeavesActivityUnrecordedWhenTheDownloadFails() {
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activitiesJson(
                activityJson("i1", "Morning Ride", "2024-11-19T07:35:18", "fit")),
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(downloadUrl("i1", "/file"))).andRespond(withServerError());

        IntervalsIcuIntegrationService.SyncResult result = service.importHistoricalSlice(user, FROM, TO);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.imported()).isZero();
        assertThat(importedActivityService.findImportedIds(user, List.of("i1"))).isEmpty();
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_ImportsOldestActivityFirst() {
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess(activitiesJson(
                activityJson("i2", "Later", "2024-11-20T07:35:18", "fit"),
                activityJson("i1", "Earlier", "2024-11-19T07:35:18", "fit")),
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(downloadUrl("i1", "/file")))
                .andRespond(withSuccess(gzip("/data/fit/sample.fit"), MediaType.APPLICATION_OCTET_STREAM));
        mockServer.expect(requestTo(downloadUrl("i2", "/file")))
                .andRespond(withSuccess(gzip("/data/fit/sample.fit"), MediaType.APPLICATION_OCTET_STREAM));

        IntervalsIcuIntegrationService.SyncResult result = service.importHistoricalSlice(user, FROM, TO);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.imported()).isEqualTo(2);
        // Strict expectation order proves i1 was processed before i2.
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_ResolvesAndStoresTheLinkedAthleteOnlyOnce() {
        User fresh = testingService.randomUser();
        integrationJdbcService.save(fresh, new IntervalsIcuIntegration(
                API_KEY, testingService.findDefaultDevice(fresh).id(), true));

        mockServer.expect(requestTo(BASE_URL + "/api/v1/athlete/0"))
                .andRespond(withSuccess("{\"id\":\"i999\",\"name\":\"Fresh Athlete\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.importHistoricalSlice(fresh, FROM, TO);

        assertThat(integrationJdbcService.findByUser(fresh).orElseThrow().getAthleteName()).isEqualTo("Fresh Athlete");
        assertThat(integrationJdbcService.findByUser(fresh).orElseThrow().getAthleteId()).isEqualTo("i999");
        mockServer.verify();

        mockServer.reset();
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        service.importHistoricalSlice(fresh, FROM, TO);
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_KeepsWorkingWhenTheAthleteCannotBeResolved() {
        User fresh = testingService.randomUser();
        integrationJdbcService.save(fresh, new IntervalsIcuIntegration(
                API_KEY, testingService.findDefaultDevice(fresh).id(), true));

        mockServer.expect(requestTo(BASE_URL + "/api/v1/athlete/0")).andRespond(withServerError());
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.importHistoricalSlice(fresh, FROM, TO);

        assertThat(integrationJdbcService.findByUser(fresh).orElseThrow().getAthleteName()).isNull();
        mockServer.verify();
    }

    @Test
    void importHistoricalSlice_WithoutDates_Throws() {
        assertThatThrownBy(() -> service.importHistoricalSlice(user, null, TO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.importHistoricalSlice(user, FROM, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importHistoricalSlice_WithInvertedRange_Throws() {
        assertThatThrownBy(() -> service.importHistoricalSlice(user, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start date");
    }

    @Test
    void syncIncremental_AdvancesTheWatermarkAfterASuccessfulRun() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        integrationJdbcService.update(user, integrationJdbcService.findByUser(user).orElseThrow()
                .withLastSuccessfulFetch(today.minusDays(3).atStartOfDay().toInstant(ZoneOffset.UTC)));

        mockServer.expect(requestTo(activitiesUrl(today.minusDays(4), today))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        IntervalsIcuIntegrationService.SyncResult result = service.syncIncremental(user);

        assertThat(result.total()).isZero();
        assertThat(integrationJdbcService.findByUser(user).orElseThrow().getLastSuccessfulFetch())
                .isEqualTo(today.atTime(23, 59, 59).toInstant(ZoneOffset.UTC));
        mockServer.verify();
    }

    @Test
    void syncIncremental_WithoutWatermarkOnlyLooksAtToday() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockServer.expect(requestTo(activitiesUrl(today, today))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.syncIncremental(user);

        mockServer.verify();
    }

    @Test
    void syncIncremental_WithoutIntegration_Throws() {
        User other = testingService.randomUser();
        assertThatThrownBy(() -> service.syncIncremental(other)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testConnection_ReturnsTheAthleteName() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/athlete/0"))
                .andExpect(header("Authorization", "Basic QVBJX0tFWTp0ZXN0LWFwaS1rZXk="))
                .andRespond(withSuccess("{\"id\":\"i42\",\"name\":\"Test Athlete\",\"email\":\"a@b.c\"}", MediaType.APPLICATION_JSON));

        IntegrationTestResult result = service.testConnection(API_KEY);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Test Athlete");
        mockServer.verify();
    }

    @Test
    void testConnection_WithInvalidKey_Fails() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/athlete/0")).andRespond(withUnauthorizedRequest());

        IntegrationTestResult result = service.testConnection("wrong");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("HTTP 401");
        mockServer.verify();
    }

    private long pointsReceived(String activityId) {
        Long points = jdbcTemplate.queryForObject(
                "SELECT points_received FROM intervals_icu_imported_activities WHERE user_id = ? AND activity_id = ?",
                Long.class, user.getId(), activityId);
        return points != null ? points : 0L;
    }

    private static String activitiesUrl(LocalDate from, LocalDate to) {
        return BASE_URL + "/api/v1/athlete/0/activities?oldest=%s&newest=%s&limit=100&fields=%s"
                .formatted(from, to, "id,file_type,start_date_local");
    }

    private static String downloadUrl(String activityId, String suffix) {
        return BASE_URL + "/api/v1/activity/" + activityId + suffix;
    }

    private static String activitiesJson(String... activities) {
        return "[" + String.join(",", activities) + "]";
    }

    /**
     * Shaped like a real intervals.icu activity. The endpoint is asked for a three field projection, but this
     * deliberately keeps the surrounding properties that the service also omits, including trainer and name
     * being null, so the binding stays proven against an unprojected full object too.
     */
    private static String activityJson(String id, String name, String startDateLocal, String fileType) {
        return """
                {"id":"%s","name":%s,"start_date_local":"%s","file_type":"%s","trainer":null,"type":"GravelRide",\
                "distance":18760.73,"moving_time":2867,"timezone":null,"gear":null,"trimp":null,"icu_pm_cp":null,\
                "stream_types":["time","cadence","distance","altitude","latlng"],"recording_stops":[-4,219,664],\
                "external_id":"23592576643_ACTIVITY.fit","device_name":"GARMIN EDGE_840","source":"UPLOAD"}"""
                .formatted(id, name == null ? "null" : "\"%s\"".formatted(name), startDateLocal, fileType);
    }

    private static byte[] resource(String path) {
        try (InputStream stream = IntervalsIcuIntegrationServiceTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] gzipText(String content) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] gzip(String path) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(resource(path));
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void reportsTheHttpStatusWhenTheApiKeyIsRejected() {
        mockServer.expect(requestTo(activitiesUrl(FROM, TO))).andRespond(withUnauthorizedRequest());

        // A rejected key must surface as a clean 401, not as an "Error while extracting response" caused by
        // the JSON error object {"status":401,...} being read into the expected list type.
        assertThatThrownBy(() -> service.importHistoricalSlice(user, FROM, TO))
                .isInstanceOf(HttpClientErrorException.class)
                .hasMessageContaining("401");
        mockServer.verify();
    }

    @Test
    void reportsTheHttpStatusWhenRateLimited() {
        mockServer.expect(requestTo(activitiesUrl(FROM, TO)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"status\":429,\"error\":\"Rate limit exceeded\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        // Same class of failure as a rejected key: a JSON error object must never reach the list extractor.
        assertThatThrownBy(() -> service.importHistoricalSlice(user, FROM, TO))
                .isInstanceOf(HttpClientErrorException.class)
                .hasMessageContaining("429");
        mockServer.verify();
    }

    @Test
    void reportsTheEndpointWhenTheResponseShapeIsUnexpected() {
        mockServer.expect(requestTo(activitiesUrl(FROM, TO)))
                .andRespond(withSuccess("{\"status\":403,\"error\":\"Access denied\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.importHistoricalSlice(user, FROM, TO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/api/v1/athlete/0/activities")
                .hasMessageContaining("Access denied");
        mockServer.verify();
    }
}
