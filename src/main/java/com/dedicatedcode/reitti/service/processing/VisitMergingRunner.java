package com.dedicatedcode.reitti.service.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class VisitMergingRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(VisitMergingRunner.class);
    
    private final VisitMergingService visitMergingService;
    private final TripDetectionService tripDetectionService;
    
    @Value("${reitti.process-visits-on-startup:false}")
    private boolean processVisitsOnStartup;
    
    @Value("${reitti.detect-trips-after-merging:true}")
    private boolean detectTripsAfterMerging;
    
    public VisitMergingRunner(VisitMergingService visitMergingService, 
                             TripDetectionService tripDetectionService) {
        this.visitMergingService = visitMergingService;
        this.tripDetectionService = tripDetectionService;
    }
    
    @Override
    public void run(String... args) {
        if (processVisitsOnStartup) {
            logger.info("Starting visit merging process on application startup");
            visitMergingService.processAndMergeVisitsForAllUsers();
            logger.info("Completed visit merging process");
            
            if (detectTripsAfterMerging) {
                logger.info("Starting trip detection process after visit merging");
                tripDetectionService.detectTripsForAllUsers();
                logger.info("Completed trip detection process");
            }
        } else {
            logger.info("Visit merging on startup is disabled. Set reitti.process-visits-on-startup=true to enable.");
        }
    }
}
