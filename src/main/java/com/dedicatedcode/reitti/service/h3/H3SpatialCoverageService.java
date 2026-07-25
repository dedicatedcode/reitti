package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.service.SpatialCoverageService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.uber.h3core.H3Core;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class H3SpatialCoverageService implements SpatialCoverageService {

    private final H3Core h3;
    private final JobSchedulingService jobSchedulingService;
    private final JobDetail h3CellUpdateJob;

    public H3SpatialCoverageService(JobSchedulingService jobSchedulingService,
                                    @Qualifier("h3CellUpdateTask") JobDetail h3CellUpdateJob) throws IOException {
        this.jobSchedulingService = jobSchedulingService;
        this.h3CellUpdateJob = h3CellUpdateJob;
        this.h3 = H3Core.newInstance();
    }

    @Override
    public Long getLevelCellForPoint(double latitude, double longitude, int resolution) {
        return h3.latLngToCell(latitude, longitude, resolution);
    }

    @Override
    public void postPromotion(List<Long> insertedIds) {
        this.jobSchedulingService.enqueueTask(h3CellUpdateJob,
                                              H3CellUpdateJob.TaskData.forPromotion(insertedIds),
                                              JobSchedulingService.Metadata.builder()
                                                      .jobType(JobType.H3_CELL_UPDATE)
                                                      .friendlyName("Updating H3 Spatial Statistics")
                                                      .build()
        );
    }
}
