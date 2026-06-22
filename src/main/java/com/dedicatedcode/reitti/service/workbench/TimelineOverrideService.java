package com.dedicatedcode.reitti.service.workbench;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TimelineOverrideService {
    private final JdbcTemplate template;

    public TimelineOverrideService(JdbcTemplate template) {
        this.template = template;
    }

    @Transactional
    public void setTimelineOverride(User user, Device device, Instant start, Instant end) {
        Long userId = user.getId();
        Timestamp startTs = Timestamp.from(start);
        Timestamp endTs = Timestamp.from(end);

        if (device == null) {
            // Clear all overrides overlapping this time range
            template.update(
                    "DELETE FROM timeline_overrides WHERE user_id = ? AND tstzrange(start_time, end_time) && tstzrange(?, ?)",
                    userId, startTs, endTs
            );
            return;
        }

        Long deviceId = device.id();

        // Fetch all overlapping rows for the user
        List<Map<String, Object>> overlappingRows = template.queryForList(
                "SELECT id, device_id, start_time, end_time FROM timeline_overrides WHERE user_id = ? AND tstzrange(start_time, end_time) && tstzrange(?, ?)",
                userId, startTs, endTs
        );

        List<Map<String, Object>> sameDeviceRows = new ArrayList<>();
        List<Map<String, Object>> otherDeviceRows = new ArrayList<>();

        for (Map<String, Object> row : overlappingRows) {
            Long existingDeviceId = (Long) row.get("device_id");
            if (existingDeviceId.equals(deviceId)) {
                sameDeviceRows.add(row);
            } else {
                otherDeviceRows.add(row);
            }
        }

        // Handle same device rows: merge into a single range
        if (!sameDeviceRows.isEmpty()) {
            Instant minStart = start;
            Instant maxEnd = end;
            for (Map<String, Object> row : sameDeviceRows) {
                Instant rowStart = ((Timestamp) row.get("start_time")).toInstant();
                Instant rowEnd = ((Timestamp) row.get("end_time")).toInstant();
                if (rowStart.isBefore(minStart)) minStart = rowStart;
                if (rowEnd.isAfter(maxEnd)) maxEnd = rowEnd;
                // Delete the old row
                template.update("DELETE FROM timeline_overrides WHERE id = ?", row.get("id"));
            }
            // Insert the merged row
            template.update(
                    "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                    userId, deviceId, Timestamp.from(minStart), Timestamp.from(maxEnd)
            );
        }

        // Handle other device rows: trim/split to accommodate the new range
        for (Map<String, Object> row : otherDeviceRows) {
            Long id = (Long) row.get("id");
            Long existingDeviceId = (Long) row.get("device_id");
            Timestamp rowStart = (Timestamp) row.get("start_time");
            Timestamp rowEnd = (Timestamp) row.get("end_time");

            Instant rowStartInstant = rowStart.toInstant();
            Instant rowEndInstant = rowEnd.toInstant();

            // Case 1: existing row fully contains the new range → split
            if (rowStartInstant.isBefore(start) && rowEndInstant.isAfter(end)) {
                template.update("DELETE FROM timeline_overrides WHERE id = ?", id);
                // left part
                template.update(
                        "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                        userId, existingDeviceId, rowStart, startTs
                );
                // right part
                template.update(
                        "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                        userId, existingDeviceId, endTs, rowEnd
                );
            }
            // Case 2: overlap only at the start of the existing row → trim end
            else if (rowStartInstant.isBefore(start) && rowEndInstant.isAfter(start) && !rowEndInstant.isAfter(end)) {
                template.update(
                        "UPDATE timeline_overrides SET end_time = ? WHERE id = ?",
                        startTs, id
                );
            }
            // Case 3: overlap only at the end of the existing row → trim start
            else if (rowStartInstant.isBefore(end) && rowEndInstant.isAfter(end) && !rowStartInstant.isBefore(start)) {
                template.update(
                        "UPDATE timeline_overrides SET start_time = ? WHERE id = ?",
                        endTs, id
                );
            }
            // Case 4: existing row fully inside the new range → delete
            else {
                template.update("DELETE FROM timeline_overrides WHERE id = ?", id);
            }
        }

        // If we did not already insert a merged same-device row, insert the new override
        if (sameDeviceRows.isEmpty()) {
            template.update(
                    "INSERT INTO timeline_overrides (user_id, device_id, start_time, end_time) VALUES (?, ?, ?, ?)",
                    userId, deviceId, startTs, endTs
            );
        }
    }
}