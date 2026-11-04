package com.dedicatedcode.reitti.service.geocoding;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceOverrideJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.JobContext;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class BulkReverseGeocodingListener implements Job {
    private static final Logger logger = LoggerFactory.getLogger(BulkReverseGeocodingListener.class);

    private final SignificantPlaceJdbcService significantPlaceJdbcService;
    private final SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    private final UserJdbcService userJdbcService;
    private final ReverseGeocodingListener reverseGeocodingListener;
    private final JobMetadataRepository jobMetadataRepository;

    @Autowired
    public BulkReverseGeocodingListener(SignificantPlaceJdbcService significantPlaceJdbcService,
                                        SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService,
                                        UserJdbcService userJdbcService,
                                        ReverseGeocodingListener reverseGeocodingListener,
                                        JobMetadataRepository jobMetadataRepository) {
        this.significantPlaceJdbcService = significantPlaceJdbcService;
        this.significantPlaceOverrideJdbcService = significantPlaceOverrideJdbcService;
        this.userJdbcService = userJdbcService;
        this.reverseGeocodingListener = reverseGeocodingListener;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        execute(TaskData.fromJson((String) dataMap.get("data")));
    }

    public void execute(TaskData event) {
        logger.info("Starting bulk reverse geocoding for user: {} (clearBefore={})", event.username(), event.clearBefore());
        User user = userJdbcService.findByUsername(event.username()).orElseThrow();

        if (event.clearBefore()) {
            List<SignificantPlace> allPlaces = significantPlaceJdbcService.findAllByUser(user);
            int clearTotal = allPlaces.size();
            int clearProcessed = 0;
            for (SignificantPlace place : allPlaces) {
                try {
                    SignificantPlace clearedPlace = place.withGeocoded(false).withAddress(null);
                    significantPlaceOverrideJdbcService.clear(user, clearedPlace);
                    significantPlaceJdbcService.update(user, clearedPlace);
                } catch (Exception e) {
                    logger.error("Error during clearing geocoding data for place ID: {}", place.getId(), e);
                }
                clearProcessed++;
                jobMetadataRepository.updateProgress(event.getJobId(), clearProcessed, clearTotal, "Clearing existing addresses ...");
            }
        }

        // both modes process all places that currently have no address
        List<SignificantPlace> places = significantPlaceJdbcService.findNonGeocodedByUser(user);
        int total = places.size();
        int processed = 0;

        for (SignificantPlace place : places) {
            try {
                reverseGeocodingListener.geocodePlace(user, place, null, true);
            } catch (Exception e) {
                logger.error("Error during reverse geocoding for place ID: {}", place.getId(), e);
            }
            processed++;
            jobMetadataRepository.updateProgress(event.getJobId(), processed, total, "Reverse geocoding places ...");
        }

        logger.info("Finished bulk reverse geocoding for user: {}", event.username());
    }

    public static final class TaskData extends JobContext<TaskData> {
        private final String username;
        private final boolean clearBefore;

        @JsonCreator
        public TaskData(@JsonProperty("username") String username,
                        @JsonProperty("clearBefore") boolean clearBefore,
                        @JsonProperty("jobId") UUID jobId,
                        @JsonProperty("parentJobId") UUID parentJobId) {
            super(jobId, parentJobId);
            this.username = username;
            this.clearBefore = clearBefore;
        }

        public TaskData(String username, boolean clearBefore) {
            this(username, clearBefore, null, null);
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
        }

        public String username() {
            return username;
        }

        public boolean clearBefore() {
            return clearBefore;
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(username, clearBefore, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(username, clearBefore, jobId, parentJobId);
        }
    }
}
