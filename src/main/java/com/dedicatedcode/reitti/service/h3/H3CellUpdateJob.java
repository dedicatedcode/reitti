package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.service.JobContext;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@DisallowConcurrentExecution
public class H3CellUpdateJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(H3CellUpdateJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final RocksDBH3Service rocksDbService;

    public H3CellUpdateJob(JdbcTemplate jdbcTemplate, RocksDBH3Service rocksDbService) {
        this.jdbcTemplate = jdbcTemplate;
        this.rocksDbService = rocksDbService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TaskData data = (TaskData) context.getMergedJobDataMap().get("data");

        log.debug("Updating H3 Spatial Statistics for {} new promoted ids", data.newPromotedIds.size());


    }

    private record PointData(long userId, Long deviceid, long id, double lat, double lng, long h3Cell, int status) {}

    public static class TaskData extends JobContext<TaskData> {
        private final List<Long> newPromotedIds;
        public TaskData(List<Long> newPromotedIds) {
            this.newPromotedIds = newPromotedIds;
        }

        private TaskData(UUID jobId, UUID parentJobId, List<Long> newPromotedIds) {
            super(jobId, parentJobId);
            this.newPromotedIds = newPromotedIds;
        }

        public static TaskData forPromotion(List<Long> newPromotedIds) {
            return new TaskData(newPromotedIds);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(jobId, parentJobId, newPromotedIds);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(jobId, parentJobId, newPromotedIds);
        }
    }
}
