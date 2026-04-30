package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.controller.api.QueueStatsApiController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobInfo(
        UUID id,
        String name,
        String description,
        JobState state,
        Instant enqueuedAt,
        Instant scheduledAt,
        Instant processingAt,
        Instant finishedAt,
        boolean canCancel,
        List<JobInfo> children,
        long completedChildren,
        long totalChildren,
        Long durationSeconds
) {
    public String getProgressText() {
        if (totalChildren > 0) {
            return completedChildren + " / " + totalChildren + " child jobs";
        }
        return null;
    }

    public int getProgressPercent() {
        if (totalChildren > 0) {
            return (int) ((completedChildren * 100) / totalChildren);
        }
        return 0;
    }

    public String getFormattedDuration() {
        if (durationSeconds == null || durationSeconds == 0) {
            return null;
        }
        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
