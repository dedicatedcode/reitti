package com.dedicatedcode.reitti;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.UserService;
import com.dedicatedcode.reitti.service.importer.GeoJsonImporter;
import com.dedicatedcode.reitti.service.importer.GpxImporter;
import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.Scheduler;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TestingService {

    @Autowired
    private UserJdbcService userJdbcService;
    @Autowired
    private GpxImporter gpxImporter;
    @Autowired
    private GeoJsonImporter geoJsonImporter;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointRepository;
    @Autowired
    private TripJdbcService tripRepository;
    @Autowired
    private ProcessedVisitJdbcService processedVisitRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private SignificantPlaceJdbcService significantPlaceJdbcService;
    @Autowired
    private ApiTokenJdbcService apiTokenJdbcService;
    @Autowired
    private DeviceJdbcService deviceJdbcService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Scheduler scheduler;

    public void importData(User user, String path) {
        Device device = findDefaultDevice(user);
        importData(user, device, path);
    }
    public void importData(User user, Device device, String path) {
        InputStream is = getClass().getResourceAsStream(path);
        if (path.endsWith(".gpx")) {
            gpxImporter.importGpx(is, user, device, null);
        } else if (path.endsWith(".geojson")) {
            geoJsonImporter.importGeoJson(is, user, device, null);
        } else {
            throw new IllegalStateException("Unsupported file type: " + path);
        }
    }

    public User admin() {
        return this.userJdbcService.findById(1L)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + (Long) 1L));
    }

    public User randomUser() {
        return this.userService.createNewUser("test-user_" + UUID.randomUUID(),"Test User", null, null);
    }

    public void awaitDataImport(int seconds) {
        AtomicLong lastRawCount = new AtomicLong(-1);
        AtomicLong lastTripCount = new AtomicLong(-1);
        AtomicInteger stableChecks = new AtomicInteger(0);

        // Require multiple consecutive stable checks
        final int requiredStableChecks = 5;

        Awaitility.await()
                .logging()
                .pollInterval(Math.max(1, seconds / 300), TimeUnit.SECONDS)
                .atMost(seconds, TimeUnit.SECONDS)
                .alias("Wait for processing to complete")
                .until(() -> {
                    List<ScheduledExecution<Object>> instances = scheduler.getScheduledExecutions()
                            .stream().filter(t -> !t.getTaskInstance().getTaskName().equals("sse-emitter-task")).toList();

                    if (!instances.isEmpty()) {
                        stableChecks.set(0);
                        return false;
                    }

                    // Check if all counts are stable
                    long currentRawCount = rawLocationPointRepository.count();
                    long currentTripCount = tripRepository.count();

                    boolean countsStable =
                            currentRawCount == lastRawCount.get() &&
                                    currentTripCount == lastTripCount.get();

                    lastRawCount.set(currentRawCount);
                    lastTripCount.set(currentTripCount);

                    if (countsStable) {
                        return stableChecks.incrementAndGet() >= requiredStableChecks;
                    } else {
                        stableChecks.set(0);
                        return false;
                    }
                });
    }

    public void clearData() {
        //now clear the database
        this.tripRepository.deleteAll();
        this.processedVisitRepository.deleteAll();
        this.rawLocationPointRepository.deleteAll();
    }

    public void importAndProcess(User user, String path) {
        importData(user, path);
        awaitDataImport(100);
    }
    public void importAndProcess(User user, Device device, String path) {
        importData(user, device, path);
        awaitDataImport(100);
    }

    public SignificantPlace newSignificantPlace(User user) {
        return newSignificantPlace(user, 53.48278089848833, 9.32412809124706, null);
    }

    public SignificantPlace newSignificantPlace(User user, String name) {
        return newSignificantPlace(user, 53.48278089848833, 9.32412809124706, name);
    }

    public SignificantPlace newSignificantPlace(User user, double latitude, double longitude, String name) {
        SignificantPlace significantPlace = this.significantPlaceJdbcService.create(user, SignificantPlace.create(latitude, longitude));
        if (name != null) {
            return this.significantPlaceJdbcService.update(significantPlace.withName(name));
        } else {
            return significantPlace;
        }
    }

    public ApiToken createApiToken(User user, String name, Device device) {
        return this.apiTokenJdbcService.save(new ApiToken(user, name, device));
    }

    public Device createRandomDevice(User user) {
        Instant now = Instant.now();
        Device device = new Device(
                null,
                "test-device_" + UUID.randomUUID(),
                true,
                true,
                "#3e3e3e",
                false,
                now,
                now,
                1L
        );
        Device saved = deviceJdbcService.save(device, user);
        ApiToken apiToken = new ApiToken(user, saved.name(), saved);
        this.apiTokenJdbcService.save(apiToken);
        return saved;
    }

    public Device findDefaultDevice(User user) {
        return this.deviceJdbcService.getAll(user).stream().filter(Device::defaultDevice).findFirst().orElseThrow();
    }

    public ProcessedVisit createVisit(User user, SignificantPlace place, Instant start, Instant end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO processed_visits (user_id, place_id, start_time, end_time, duration_seconds, metadata) VALUES (?,?,?,?,?,?::jsonb) RETURNING id",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, user.getId());
            ps.setLong(2, place.getId());
            ps.setTimestamp(3, Timestamp.from(start));
            ps.setTimestamp(4, Timestamp.from(end));
            ps.setLong(5, end.getEpochSecond() - start.getEpochSecond());
            ps.setString(6, "{}");
            return ps;
        }, keyHolder);
        return this.processedVisitRepository.findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    public Trip createTrip(User user, ProcessedVisit startVisit, ProcessedVisit endVisit) {
        return createTrip(user, startVisit, endVisit, TransportMode.UNKNOWN);
    }

    public Trip createTrip(User user, ProcessedVisit startVisit, ProcessedVisit endVisit, TransportMode transportMode) {
        Instant start = startVisit.getEndTime();
        Instant end = endVisit.getStartTime();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, estimated_distance_meters, travelled_distance_meters, transport_mode_inferred, start_visit_id, end_visit_id, metadata) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb) RETURNING id",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, user.getId());
            ps.setTimestamp(2, Timestamp.from(start));
            ps.setTimestamp(3, Timestamp.from(end));
            long duration = end.getEpochSecond() - start.getEpochSecond();
            ps.setLong(4, duration);
            ps.setDouble(5, 0.0);
            ps.setDouble(6, 0.0);
            ps.setString(7, transportMode.name());
            ps.setLong(8, startVisit.getId());
            ps.setLong(9, endVisit.getId());
            ps.setString(10, "{}");
            return ps;
        }, keyHolder);
        return this.tripRepository.findById(keyHolder.getKey().longValue()).orElseThrow();
    }
}
