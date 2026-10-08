package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.integration.IntervalsIcuIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.IntervalsIcuImportedActivityJdbcService;
import com.dedicatedcode.reitti.repository.IntervalsIcuIntegrationJdbcService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises the self-deferring historical import end to end. The slice size is pinned to one activity so a
 * three activity history forces the job to defer twice, which is the interesting part: every resumed slice has
 * to continue where the previous one stopped instead of starting over or skipping days.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "reitti.imports.intervals-icu.max-activities-per-run=1",
        "reitti.imports.intervals-icu.slice-delay-seconds=1"
})
class IntervalsIcuHistoricalImportTaskTest {

    private static final String BASE_URL = "https://intervals.icu";
    private static final String API_KEY = "test-api-key";
    private static final LocalDate HISTORY_START = LocalDate.parse("2010-01-01");
    private static final LocalDate DAY_1 = LocalDate.parse("2024-11-17");
    private static final LocalDate DAY_2 = LocalDate.parse("2024-11-18");
    private static final LocalDate DAY_3 = LocalDate.parse("2024-11-19");

    @Autowired
    private IntervalsIcuIntegrationService integrationService;

    @Autowired
    private IntervalsIcuIntegrationJdbcService integrationJdbcService;

    @Autowired
    private IntervalsIcuImportedActivityJdbcService importedActivityService;

    @Autowired
    private JobSchedulingService jobSchedulingService;

    @Autowired
    @Qualifier("intervalsIcuHistoricalImportJob")
    private JobDetail historicalImportJob;

    @Autowired
    private TestingService testingService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockRestServiceServer mockServer;
    private ClientHttpRequestFactory originalRequestFactory;
    private User user;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        originalRequestFactory = restTemplate.getRequestFactory();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        today = LocalDate.now(ZoneOffset.UTC);
        user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        integrationJdbcService.save(user,
                new IntervalsIcuIntegration(API_KEY, device.id(), true).withAthlete("i1", "Test Athlete"));
    }

    @AfterEach
    void tearDown() {
        restTemplate.setRequestFactory(originalRequestFactory);
        testingService.clearData();
    }

    @Test
    void defersUntilTheWholeHistoryIsImportedWithoutDownloadingAnythingTwice() {
        // Slice one sees the whole history and takes the oldest activity.
        expectActivities(HISTORY_START, activity("i1", DAY_1), activity("i2", DAY_2), activity("i3", DAY_3));
        expectDownload("i1");
        // Slice two resumes on day one, so day one is listed again but only the unimported ones are taken.
        expectActivities(DAY_1, activity("i1", DAY_1), activity("i2", DAY_2), activity("i3", DAY_3));
        expectDownload("i2");
        // Slice three resumes on day two and finishes on the last activity.
        expectActivities(DAY_2, activity("i2", DAY_2), activity("i3", DAY_3));
        expectDownload("i3");

        startHistoricalImport();

        awaitImportedCount(3);

        assertThat(importedActivityService.findImportedIds(user, List.of("i1", "i2", "i3")))
                .containsExactlyInAnyOrder("i1", "i2", "i3");
        // A historical import must never move the incremental watermark.
        assertThat(integrationJdbcService.findByUser(user).orElseThrow().getLastSuccessfulFetch()).isNull();
        // Strict ordered expectations fail if a download happens more than once, and verify() also fails on
        // any request the job was not allowed to make, which is how we prove it stopped instead of looping.
        Awaitility.await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> mockServer.verify());
    }

    @Test
    void stopsAfterTheFinalSliceEvenWhenTheHistoryIsFullyCoveredByASingleSlice() {
        expectActivities(HISTORY_START, activity("i1", DAY_1));
        expectDownload("i1");

        startHistoricalImport();

        awaitImportedCount(1);
        Awaitility.await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> mockServer.verify());
    }

    @Test
    void sliceReportsRemainingActivitiesAndAResumeDateOnTheLastProcessedDay() {
        expectActivities(HISTORY_START, activity("i1", DAY_1), activity("i2", DAY_2), activity("i3", DAY_3));
        expectDownload("i1");

        IntervalsIcuIntegrationService.SyncResult slice = integrationService.importHistoricalSlice(user, HISTORY_START, today);

        assertThat(slice.total()).isEqualTo(3);
        assertThat(slice.imported()).isEqualTo(1);
        assertThat(slice.remaining()).isEqualTo(2);
        // Resuming on the last processed day rather than the day after it keeps same day activities reachable.
        assertThat(slice.resumeFrom()).isEqualTo(DAY_1);
        mockServer.verify();
    }

    @Test
    void sliceReportsNoWorkLeftWhenEverythingInTheWindowIsImported() {
        expectActivities(HISTORY_START, activity("i1", DAY_1));
        expectDownload("i1");

        IntervalsIcuIntegrationService.SyncResult slice = integrationService.importHistoricalSlice(user, HISTORY_START, today);

        assertThat(slice.remaining()).isZero();
        assertThat(slice.resumeFrom()).isNull();
        mockServer.verify();
    }

    private void startHistoricalImport() {
        JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                .user(user)
                .jobType(JobType.INTERVALS_ICU_IMPORT)
                .friendlyName("Load Historical Data")
                .build();
        jobSchedulingService.enqueueTask(historicalImportJob,
                new IntervalsIcuHistoricalImportTask.TaskData(user.getId(), HISTORY_START, today),
                metadata);
    }

    private void awaitImportedCount(int expected) {
        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() ->
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM intervals_icu_imported_activities WHERE user_id = ?",
                        Integer.class, user.getId()) == expected);
    }

    private void expectActivities(LocalDate oldest, ExpectedActivity... activities) {
        String url = ("%s/api/v1/athlete/0/activities?oldest=%s&newest=%s&limit=100&fields=%s"
                .formatted(BASE_URL, oldest, today, "id,file_type,start_date_local"));
        // intervals.icu returns activities newest first. Emitting them in that order matters, because the
        // importer relies on it to walk a window oldest first.
        String[] json = Arrays.stream(activities)
                .sorted(Comparator.comparing(ExpectedActivity::date).reversed())
                .map(ExpectedActivity::toJson)
                .toArray(String[]::new);
        mockServer.expect(requestTo(url))
                .andRespond(withSuccess("[" + String.join(",", json) + "]", MediaType.APPLICATION_JSON));
    }

    private void expectDownload(String activityId) {
        mockServer.expect(requestTo("%s/api/v1/activity/%s/file".formatted(BASE_URL, activityId)))
                .andRespond(withSuccess(gzip("/data/fit/sample.fit"), MediaType.APPLICATION_OCTET_STREAM));
    }

    private static ExpectedActivity activity(String id, LocalDate date) {
        return new ExpectedActivity(id, date);
    }

    private record ExpectedActivity(String id, LocalDate date) {
        String toJson() {
            return """
                    {"id":"%s","name":null,"start_date_local":"%sT07:35:18","file_type":"fit","trainer":null,\
                    "type":"Ride","timezone":null,"gear":null,"stream_types":["time","distance","altitude","latlng"]}"""
                    .formatted(id, date);
        }
    }

    private static byte[] gzip(String path) {
        try (InputStream stream = IntervalsIcuHistoricalImportTaskTest.class.getResourceAsStream(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            stream.transferTo(gzip);
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void recordsWhyTheImportFailedSoTheJobStatusPageCanShowIt() {
        // A JSON error object must never reach the activity list extractor, and whatever goes wrong has to
        // end up in progress_message. The job status page renders that text for failed jobs and nothing else.
        mockServer.expect(requestTo("%s/api/v1/athlete/0/activities?oldest=%s&newest=%s&limit=100&fields=%s"
                        .formatted(BASE_URL, HISTORY_START, today, "id,file_type,start_date_local")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"status\":429,\"error\":\"Rate limit exceeded\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        startHistoricalImport();

        testingService.awaitExpected(db -> {
            String message = db.queryForObject(
                    "SELECT progress_message FROM job_meta_data WHERE user_id = ? AND type = ? AND status = ?",
                    String.class, user.getId(), JobType.INTERVALS_ICU_IMPORT.name(), JobState.FAILED.name());
            return message != null && message.contains("429");
        }, 60);
    }
}
