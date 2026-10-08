package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.service.h3.H3CellUpdateJob;
import com.dedicatedcode.reitti.service.integration.IntervalsIcuHistoricalImportTask;
import com.dedicatedcode.reitti.service.processing.ProcessingPipelineTask;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobContextJsonTest {

    @Test
    void shouldRoundTripProcessingPipelineTaskData() {
        UUID jobId = UUID.randomUUID();
        UUID parentJobId = UUID.randomUUID();
        Instant receivedAt = Instant.parse("2026-09-13T10:15:30Z");
        ProcessingPipelineTask.TaskData original = new ProcessingPipelineTask.TaskData(
                "tester", "preview-1", "trace-1", receivedAt, jobId, parentJobId, false);

        String json = original.toJson();
        ProcessingPipelineTask.TaskData restored = ProcessingPipelineTask.TaskData.fromJson(json);

        assertEquals(jobId, restored.getJobId());
        assertEquals(parentJobId, restored.getParentJobId());
        assertEquals("tester", restored.getUsername());
        assertEquals("preview-1", restored.getPreviewId());
        assertEquals("trace-1", restored.getTraceId());
        assertEquals(receivedAt, restored.getReceivedAt());
        assertFalse(restored.isFullReprocess());
    }

    @Test
    void shouldRoundTripFullReprocessFlag() {
        ProcessingPipelineTask.TaskData restored = ProcessingPipelineTask.TaskData.fromJson(
                new ProcessingPipelineTask.TaskData("tester", null, null)
                        .withFullReprocess()
                        .withJobId(UUID.randomUUID())
                        .toJson());

        assertTrue(restored.isFullReprocess());
    }

    @Test
    void shouldRoundTripH3TaskDataWithRecordsAndEnums() {
        UUID jobId = UUID.randomUUID();
        List<H3CellUpdateJob.MovedPoint> movedPoints = List.of(new H3CellUpdateJob.MovedPoint(1L, 10.0, 20.0, 10.1, 20.1, 123L, 456L));

        H3CellUpdateJob.TaskData original = H3CellUpdateJob.TaskData.forMovement(movedPoints).withJobId(jobId);

        H3CellUpdateJob.TaskData restored = H3CellUpdateJob.TaskData.fromJson(original.toJson());

        assertEquals(jobId, restored.getJobId());
        assertEquals(original.toJson(), restored.toJson());
    }

    @Test
    void shouldRoundTripH3TaskDataWithCellIncrements() {
        List<H3CellUpdateJob.CellIncrement> cellIncrements = List.of(new H3CellUpdateJob.CellIncrement(1L, 3L, 789L, 2, Instant.now(), Instant.now().minusSeconds(60)));

        H3CellUpdateJob.TaskData original = H3CellUpdateJob.TaskData.forIncrement(cellIncrements).withJobId(UUID.randomUUID());

        H3CellUpdateJob.TaskData restored = H3CellUpdateJob.TaskData.fromJson(original.toJson());

        assertEquals(original.toJson(), restored.toJson());
    }

    @Test
    void shouldIgnoreUnknownPropertiesFromOlderVersions() {
        ProcessingPipelineTask.TaskData original = new ProcessingPipelineTask.TaskData("tester", null, null);
        String json = original.toJson();
        // simulates a field that existed in a previous app version but is gone now
        String legacyJson = json.replace("{", "{\"removedField\":\"legacy-value\",");

        ProcessingPipelineTask.TaskData restored = ProcessingPipelineTask.TaskData.fromJson(legacyJson);

        assertEquals("tester", restored.getUsername());
        assertNull(restored.getPreviewId());
    }

    @Test
    void shouldNotContainQuartzIncompatibleValues() {
        ProcessingPipelineTask.TaskData data = new ProcessingPipelineTask.TaskData("tester", null, null).withJobId(UUID.randomUUID());
        String json = data.toJson();
        // the whole payload must survive as a single string - no line breaks that could
        // break quartz property storage
        assertFalse(json.contains("\n"));
    }

    @Test
    void shouldRoundTripIntervalsIcuHistoricalImportTaskData() {
        UUID jobId = UUID.randomUUID();
        UUID parentJobId = UUID.randomUUID();
        IntervalsIcuHistoricalImportTask.TaskData original =
                new IntervalsIcuHistoricalImportTask.TaskData(42L, LocalDate.parse("2024-11-17"), LocalDate.parse("2026-10-08"))
                        .withJobId(jobId)
                        .withParentJobId(parentJobId);

        String json = original.toJson();
        IntervalsIcuHistoricalImportTask.TaskData restored = IntervalsIcuHistoricalImportTask.TaskData.fromJson(json);

        assertEquals(jobId, restored.getJobId());
        assertEquals(parentJobId, restored.getParentJobId());
        assertEquals(42L, restored.userId());
        assertEquals(LocalDate.parse("2024-11-17"), restored.fromDate());
        assertEquals(LocalDate.parse("2026-10-08"), restored.toDate());
        // Dates are kept as plain strings because JobContext serializes with a mapper that has no
        // JavaTimeModule, and the payload must survive as a single String for Quartz property storage.
        assertFalse(json.contains("\n"));
        assertTrue(json.contains("\"2024-11-17\""));
    }

    @Test
    void shouldResumeIntervalsIcuHistoricalImportFromTheAdvancedCursor() {
        IntervalsIcuHistoricalImportTask.TaskData data =
                new IntervalsIcuHistoricalImportTask.TaskData(42L, LocalDate.parse("2010-01-01"), LocalDate.parse("2026-10-08"))
                        .withJobId(UUID.randomUUID())
                        .withFromDate("2024-11-17");

        IntervalsIcuHistoricalImportTask.TaskData restored =
                IntervalsIcuHistoricalImportTask.TaskData.fromJson(data.toJson());

        assertEquals(LocalDate.parse("2024-11-17"), restored.fromDate());
        assertEquals(LocalDate.parse("2026-10-08"), restored.toDate());
    }

    @Test
    void shouldTolerateUnknownFieldsInIntervalsIcuHistoricalImportTaskData() {
        String json = new IntervalsIcuHistoricalImportTask.TaskData(42L, LocalDate.parse("2024-11-17"), LocalDate.parse("2026-10-08"))
                .toJson().replace("{", "{\"removedField\":\"legacy-value\",");

        IntervalsIcuHistoricalImportTask.TaskData restored = IntervalsIcuHistoricalImportTask.TaskData.fromJson(json);

        assertEquals(42L, restored.userId());
        assertEquals(LocalDate.parse("2024-11-17"), restored.fromDate());
    }
}
