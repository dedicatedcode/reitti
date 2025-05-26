package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VisitMergingService {
    
    private static final Logger logger = LoggerFactory.getLogger(VisitMergingService.class);
    
    private final VisitRepository visitRepository;
    private final ProcessedVisitRepository processedVisitRepository;
    private final UserRepository userRepository;
    
    @Value("${reitti.visit.merge-threshold-seconds:300}")
    private long mergeThresholdSeconds;
    
    public VisitMergingService(VisitRepository visitRepository, 
                              ProcessedVisitRepository processedVisitRepository,
                              UserRepository userRepository) {
        this.visitRepository = visitRepository;
        this.processedVisitRepository = processedVisitRepository;
        this.userRepository = userRepository;
    }
    
    @Transactional
    public List<ProcessedVisit> processAndMergeVisits(User user) {
        logger.info("Processing and merging visits for user: {}", user.getUsername());
        
        // Get all visits for the user
        List<Visit> allVisits = visitRepository.findByUser(user);
        
        if (allVisits.isEmpty()) {
            logger.info("No visits found for user: {}", user.getUsername());
            return Collections.emptyList();
        }
        
        // Group visits by place
        Map<SignificantPlace, List<Visit>> visitsByPlace = allVisits.stream()
                .collect(Collectors.groupingBy(Visit::getPlace));
        
        List<ProcessedVisit> processedVisits = new ArrayList<>();
        
        // Process each place separately
        for (Map.Entry<SignificantPlace, List<Visit>> entry : visitsByPlace.entrySet()) {
            SignificantPlace place = entry.getKey();
            List<Visit> visits = entry.getValue();
            
            // Sort visits by start time
            visits.sort(Comparator.comparing(Visit::getStartTime));
            
            // Process visits for this place
            List<ProcessedVisit> mergedVisitsForPlace = mergeVisitsForPlace(user, place, visits);
            processedVisits.addAll(mergedVisitsForPlace);
        }
        
        logger.info("Processed {} visits into {} merged visits for user: {}", 
                allVisits.size(), processedVisits.size(), user.getUsername());
        
        return processedVisits;
    }
    
    @Transactional
    public List<ProcessedVisit> processAndMergeVisitsForAllUsers() {
        logger.info("Processing and merging visits for all users");
        
        List<User> allUsers = userRepository.findAll();
        List<ProcessedVisit> allProcessedVisits = new ArrayList<>();
        
        for (User user : allUsers) {
            List<ProcessedVisit> userProcessedVisits = processAndMergeVisits(user);
            allProcessedVisits.addAll(userProcessedVisits);
        }
        
        logger.info("Completed processing for all users. Total processed visits: {}", 
                allProcessedVisits.size());
        
        return allProcessedVisits;
    }
    
    private List<ProcessedVisit> mergeVisitsForPlace(User user, SignificantPlace place, List<Visit> visits) {
        List<ProcessedVisit> result = new ArrayList<>();
        
        if (visits.isEmpty()) {
            return result;
        }
        
        // Start with the first visit
        Visit currentVisit = visits.get(0);
        Instant currentStartTime = currentVisit.getStartTime();
        Instant currentEndTime = currentVisit.getEndTime();
        Set<Long> mergedVisitIds = new HashSet<>();
        mergedVisitIds.add(currentVisit.getId());
        
        for (int i = 1; i < visits.size(); i++) {
            Visit nextVisit = visits.get(i);
            
            // Check if the next visit is within the merge threshold of the current one
            Duration gap = Duration.between(currentEndTime, nextVisit.getStartTime());
            
            if (gap.getSeconds() <= mergeThresholdSeconds) {
                // Merge this visit with the current one
                currentEndTime = nextVisit.getEndTime();
                mergedVisitIds.add(nextVisit.getId());
            } else {
                // Create a processed visit from the current merged set
                ProcessedVisit processedVisit = createProcessedVisit(user, place, currentStartTime, 
                        currentEndTime, mergedVisitIds);
                result.add(processedVisit);
                
                // Start a new merged set with this visit
                currentStartTime = nextVisit.getStartTime();
                currentEndTime = nextVisit.getEndTime();
                mergedVisitIds = new HashSet<>();
                mergedVisitIds.add(nextVisit.getId());
            }
        }
        
        // Add the last merged set
        ProcessedVisit processedVisit = createProcessedVisit(user, place, currentStartTime, 
                currentEndTime, mergedVisitIds);
        result.add(processedVisit);
        
        return result;
    }
    
    private ProcessedVisit createProcessedVisit(User user, SignificantPlace place, 
                                              Instant startTime, Instant endTime, 
                                              Set<Long> originalVisitIds) {
        ProcessedVisit processedVisit = new ProcessedVisit(user, place, startTime, endTime);
        processedVisit.setMergedCount(originalVisitIds.size());
        
        // Store original visit IDs as comma-separated string
        String visitIdsStr = originalVisitIds.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        processedVisit.setOriginalVisitIds(visitIdsStr);
        
        return processedVisitRepository.save(processedVisit);
    }
    
    @Transactional
    public void clearProcessedVisits(User user) {
        List<ProcessedVisit> userVisits = processedVisitRepository.findByUser(user);
        processedVisitRepository.deleteAll(userVisits);
        logger.info("Cleared {} processed visits for user: {}", userVisits.size(), user.getUsername());
    }
    
    @Transactional
    public void clearAllProcessedVisits() {
        processedVisitRepository.deleteAll();
        logger.info("Cleared all processed visits");
    }
}
