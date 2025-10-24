package com.dedicatedcode.reitti.model.memory;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Visit;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class MemoryVisitClusterBlockDTO implements MemoryBlockPart, Serializable {

    private final MemoryClusterBlock clusterBlock;
    private final List<ProcessedVisit> visits;
    private final String rawLocationPointsUrl;
    private final LocalDateTime adjustedStartTime;
    private final LocalDateTime adjustedEndTime;
    private final Long completeDuration;

    public MemoryVisitClusterBlockDTO(MemoryClusterBlock clusterBlock, List<ProcessedVisit> visits, String rawLocationPointsUrl, LocalDateTime adjustedStartTime, LocalDateTime adjustedEndTime, Long completeDuration) {
        this.clusterBlock = clusterBlock;
        this.visits = visits != null ? List.copyOf(visits) : List.of();
        this.rawLocationPointsUrl = rawLocationPointsUrl;
        this.adjustedStartTime = adjustedStartTime;
        this.adjustedEndTime = adjustedEndTime;
        this.completeDuration = completeDuration;
    }

    public MemoryClusterBlock getClusterBlock() {
        return clusterBlock;
    }

    public List<ProcessedVisit> getVisits() {
        return visits;
    }

    // Delegate to clusterBlock for common methods
    public Long getBlockId() {
        return clusterBlock.getBlockId();
    }

    public String getTitle() {
        return clusterBlock.getTitle();
    }

    public String getDescription() {
        return clusterBlock.getDescription();
    }

    public Long getCompleteDuration() {
        return completeDuration;
    }

    // Combined info methods
    public Instant getCombinedStartTime() {
        if (visits == null || visits.isEmpty()) return null;
        return visits.stream()
                .map(ProcessedVisit::getStartTime)
                .min(Instant::compareTo)
                .orElse(null);
    }

    public Instant getCombinedEndTime() {
        if (visits == null || visits.isEmpty()) return null;
        return visits.stream()
                .map(ProcessedVisit::getEndTime)
                .max(Instant::compareTo)
                .orElse(null);
    }

    public Long getCombinedDurationSeconds() {
        if (visits == null || visits.isEmpty()) return 0L;
        return visits.stream()
                .mapToLong(ProcessedVisit::getDurationSeconds)
                .sum();
    }

    public String getRawLocationPointsUrl() {
        return rawLocationPointsUrl;
    }

    public LocalDateTime getAdjustedEndTime() {
        return adjustedEndTime;
    }

    public LocalDateTime getAdjustedStartTime() {
        return adjustedStartTime;
    }

    @Override
    public BlockType getType() {
        return BlockType.CLUSTER_VISIT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MemoryVisitClusterBlockDTO that = (MemoryVisitClusterBlockDTO) o;
        return Objects.equals(clusterBlock, that.clusterBlock);
    }

    @Override
    public int hashCode() {
        return clusterBlock != null ? clusterBlock.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MemoryClusterBlockDTO{" +
                "clusterBlock=" + clusterBlock +
                ", Visits=" + visits +
                '}';
    }
}
