package com.dedicatedcode.reitti.service.workbench;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Transactional
class TimelineOverrideServiceTest {

    @Autowired
    private TimelineOverrideService overrideService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestingService testingService;

    private User user;
    private Device device1;
    private Device device2;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
        device1 = testingService.createRandomDevice(user);
        device2 = testingService.createRandomDevice(user);
    }

    // ----- same device tests -----

    @Test
    void sameDevice_existingFullyContainsNewRange_shouldMerge() {
        // old: dev1 09:00-12:00, new: dev1 10:00-11:00 → merged: dev1 09:00-12:00
        Instant oldStart = Instant.parse("2023-06-01T09:00:00Z");
        Instant oldEnd = Instant.parse("2023-06-01T12:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                user.getId(), device1.id(), Timestamp.from(oldStart), Timestamp.from(oldEnd)
        );

        overrideService.setTimelineOverride(user, device1,
                                            Instant.parse("2023-06-01T10:00:00Z"),
                                            Instant.parse("2023-06-01T11:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM timeline_overrides WHERE user_id = ?", user.getId());
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals(device1.id(), row.get("device_id"));
        assertEquals(Timestamp.from(oldStart), row.get("start_time"));
        assertEquals(Timestamp.from(oldEnd), row.get("end_time"));
    }

    @Test
    void sameDevice_existingOverlapStart_shouldMerge() {
        // old: dev1 09:00-11:00, new: dev1 10:00-13:00 → merged: dev1 09:00-13:00
        Instant oldStart = Instant.parse("2023-06-01T09:00:00Z");
        Instant oldEnd = Instant.parse("2023-06-01T11:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                user.getId(), device1.id(), Timestamp.from(oldStart), Timestamp.from(oldEnd)
        );

        overrideService.setTimelineOverride(user, device1,
                                            Instant.parse("2023-06-01T10:00:00Z"),
                                            Instant.parse("2023-06-01T13:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM timeline_overrides WHERE user_id = ?", user.getId());
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals(device1.id(), row.get("device_id"));
        assertEquals(Timestamp.from(oldStart), row.get("start_time"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T13:00:00Z")), row.get("end_time"));
    }

    @Test
    void sameDevice_existingFullyInsideNewRange_shouldMerge() {
        // old: dev1 10:00-11:00, new: dev1 09:00-12:00 → merged: dev1 09:00-12:00
        Instant oldStart = Instant.parse("2023-06-01T10:00:00Z");
        Instant oldEnd = Instant.parse("2023-06-01T11:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                user.getId(), device1.id(), Timestamp.from(oldStart), Timestamp.from(oldEnd)
        );

        overrideService.setTimelineOverride(user, device1,
                                            Instant.parse("2023-06-01T09:00:00Z"),
                                            Instant.parse("2023-06-01T12:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM timeline_overrides WHERE user_id = ?", user.getId());
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals(device1.id(), row.get("device_id"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T09:00:00Z")), row.get("start_time"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T12:00:00Z")), row.get("end_time"));
    }

    @Test
    void sameDevice_noOverlap_shouldInsert() {
        overrideService.setTimelineOverride(user, device1,
                                            Instant.parse("2023-06-01T10:00:00Z"),
                                            Instant.parse("2023-06-01T11:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM timeline_overrides WHERE user_id = ?", user.getId());
        assertEquals(1, rows.size());
    }

    // ----- different device tests -----

    @Test
    void differentDevice_existingFullyContainsNewRange_shouldSplit() {
        // old: dev1 09:00-12:00, new: dev2 10:00-11:00 → split dev1 into 09-10 and 11-12, insert dev2 10-11
        Instant oldStart = Instant.parse("2023-06-01T09:00:00Z");
        Instant oldEnd = Instant.parse("2023-06-01T12:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                user.getId(), device1.id(), Timestamp.from(oldStart), Timestamp.from(oldEnd)
        );

        overrideService.setTimelineOverride(user, device2,
                                            Instant.parse("2023-06-01T10:00:00Z"),
                                            Instant.parse("2023-06-01T11:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM timeline_overrides WHERE user_id = ? ORDER BY start_time", user.getId());
        assertEquals(3, rows.size());

        // left part (dev1)
        Map<String, Object> left = rows.get(0);
        assertEquals(device1.id(), left.get("device_id"));
        assertEquals(Timestamp.from(oldStart), left.get("start_time"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T10:00:00Z")), left.get("end_time"));

        // new override (dev2)
        Map<String, Object> middle = rows.get(1);
        assertEquals(device2.id(), middle.get("device_id"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T10:00:00Z")), middle.get("start_time"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T11:00:00Z")), middle.get("end_time"));

        // right part (dev1)
        Map<String, Object> right = rows.get(2);
        assertEquals(device1.id(), right.get("device_id"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T11:00:00Z")), right.get("start_time"));
        assertEquals(Timestamp.from(oldEnd), right.get("end_time"));
    }

    @Test
    void differentDevice_existingOverlapStart_shouldTrimEnd() {
        // old: dev1 09:00-11:00, new: dev2 10:00-13:00 → trim dev1 end to 10:00, insert dev2 10-13
        Instant oldStart = Instant.parse("2023-06-01T09:00:00Z");
        Instant oldEnd = Instant.parse("2023-06-01T11:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                user.getId(), device1.id(), Timestamp.from(oldStart), Timestamp.from(oldEnd)
        );

        overrideService.setTimelineOverride(user, device2,
                                            Instant.parse("2023-06-01T10:00:00Z"),
                                            Instant.parse("2023-06-01T13:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM timeline_overrides WHERE user_id = ? ORDER BY start_time", user.getId());
        assertEquals(2, rows.size());

        Map<String, Object> trimmed = rows.get(0);
        assertEquals(device1.id(), trimmed.get("device_id"));
        assertEquals(Timestamp.from(oldStart), trimmed.get("start_time"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T10:00:00Z")), trimmed.get("end_time"));

        Map<String, Object> inserted = rows.get(1);
        assertEquals(device2.id(), inserted.get("device_id"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T10:00:00Z")), inserted.get("start_time"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T13:00:00Z")), inserted.get("end_time"));
    }

    @Test
    void differentDevice_existingOverlapEnd_shouldTrimStart() {
        // old: dev1 11:00-14:00, new: dev2 09:00-12:00 → trim dev1 start to 12:00, insert dev2 09-12
        Instant oldStart = Instant.parse("2023-06-01T11:00:00Z");
        Instant oldEnd = Instant.parse("2023-06-01T14:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                user.getId(), device1.id(), Timestamp.from(oldStart), Timestamp.from(oldEnd)
        );

        overrideService.setTimelineOverride(user, device2,
                                            Instant.parse("2023-06-01T09:00:00Z"),
                                            Instant.parse("2023-06-01T12:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM timeline_overrides WHERE user_id = ? ORDER BY start_time", user.getId());
        assertEquals(2, rows.size());

        Map<String, Object> inserted = rows.get(0);
        assertEquals(device2.id(), inserted.get("device_id"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T09:00:00Z")), inserted.get("start_time"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T12:00:00Z")), inserted.get("end_time"));

        Map<String, Object> trimmed = rows.get(1);
        assertEquals(device1.id(), trimmed.get("device_id"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T12:00:00Z")), trimmed.get("start_time"));
        assertEquals(Timestamp.from(oldEnd), trimmed.get("end_time"));
    }

    @Test
    void differentDevice_existingFullyInsideNewRange_shouldDelete() {
        // old: dev1 10:00-11:00, new: dev2 09:00-12:00 → delete dev1, insert dev2 09-12
        Instant oldStart = Instant.parse("2023-06-01T10:00:00Z");
        Instant oldEnd = Instant.parse("2023-06-01T11:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                user.getId(), device1.id(), Timestamp.from(oldStart), Timestamp.from(oldEnd)
        );

        overrideService.setTimelineOverride(user, device2,
                                            Instant.parse("2023-06-01T09:00:00Z"),
                                            Instant.parse("2023-06-01T12:00:00Z"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM timeline_overrides WHERE user_id = ?", user.getId());
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals(device2.id(), row.get("device_id"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T09:00:00Z")), row.get("start_time"));
        assertEquals(Timestamp.from(Instant.parse("2023-06-01T12:00:00Z")), row.get("end_time"));
    }

    // ----- null device test -----

    @Test
    void nullDevice_shouldClearOverlapping() {
        // Insert two rows for dev1, one overlapping the range, one not
        Instant overlapStart = Instant.parse("2023-06-01T10:00:00Z");
        Instant overlapEnd = overlapStart.plus(2, ChronoUnit.HOURS);
        jdbcTemplate.update(
                "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                user.getId(), device1.id(), Timestamp.from(overlapStart), Timestamp.from(overlapEnd)
        );
        Instant noOverlapStart = Instant.parse("2023-05-01T10:00:00Z");
        Instant noOverlapEnd = noOverlapStart.plus(1, ChronoUnit.HOURS);
        jdbcTemplate.update(
                "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                user.getId(), device1.id(), Timestamp.from(noOverlapStart), Timestamp.from(noOverlapEnd)
        );

        overrideService.setTimelineOverride(user, null, overlapStart, overlapEnd);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM timeline_overrides WHERE user_id = ?", user.getId());
        assertEquals(1, rows.size());
        assertEquals(Timestamp.from(noOverlapStart), rows.get(0).get("start_time"));
    }
}