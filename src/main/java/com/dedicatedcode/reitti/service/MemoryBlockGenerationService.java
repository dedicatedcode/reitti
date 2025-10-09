package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MemoryBlockGenerationService {
    private static final Logger log = LoggerFactory.getLogger(MemoryBlockGenerationService.class);
    
    // Step 1: Filtering thresholds
    private static final long MIN_VISIT_DURATION_SECONDS = 600; // 10 minutes
    
    // Step 3: Scoring weights
    private static final double WEIGHT_DURATION = 1.0;
    private static final double WEIGHT_DISTANCE = 2.0;
    private static final double WEIGHT_CATEGORY = 3.0;
    private static final double WEIGHT_NOVELTY = 1.5;
    
    // Step 4: Clustering parameters
    private static final long CLUSTER_TIME_THRESHOLD_SECONDS = 7200; // 2 hours
    private static final double CLUSTER_DISTANCE_THRESHOLD_METERS = 500; // 500 meters
    
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;

    public MemoryBlockGenerationService(ProcessedVisitJdbcService processedVisitJdbcService, TripJdbcService tripJdbcService) {
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
    }

    public List<MemoryBlock> generate(User user, Memory memory) {
        Instant startDate = memory.getStartDate();
        Instant endDate = memory.getEndDate();

        List<ProcessedVisit> allVisitsInRange = this.processedVisitJdbcService.findByUserAndTimeOverlap(user, startDate, endDate);
        List<Trip> allTripsInRange = this.tripJdbcService.findByUserAndTimeOverlap(user, startDate, endDate);
        
        // Step 1: Data Pre-processing & Filtering
        ProcessedVisit accommodation = findAccommodation(allVisitsInRange);
        List<ProcessedVisit> filteredVisits = filterVisits(allVisitsInRange, accommodation);
        
        log.info("Found {} visits after filtering (accommodation: {})", filteredVisits.size(), 
                accommodation != null ? accommodation.getPlace().getName() : "none");
        
        // Step 3: Scoring & Identifying "Interesting" Visits
        List<ScoredVisit> scoredVisits = scoreVisits(filteredVisits, accommodation);
        
        // Sort by score descending
        scoredVisits.sort(Comparator.comparingDouble(ScoredVisit::getScore).reversed());
        
        log.info("Scored {} visits, top score: {}", scoredVisits.size(), 
                scoredVisits.isEmpty() ? 0 : scoredVisits.get(0).getScore());
        
        // Step 4: Clustering & Creating a Narrative
        List<VisitCluster> clusters = clusterVisits(scoredVisits);
        
        log.info("Created {} clusters from visits", clusters.size());
        
        // Generate memory blocks from clusters
        List<MemoryBlock> blocks = new ArrayList<>();
        int position = 0;
        
        for (VisitCluster cluster : clusters) {
            // Create a VISIT block for the cluster
            MemoryBlock visitBlock = new MemoryBlock(memory.getId(), BlockType.VISIT, position++);
            blocks.add(visitBlock);
            
            // If there are trips between clusters, add TRIP blocks
            // This would require more sophisticated logic to match trips to visit sequences
        }
        
        // Add significant trips as separate blocks
        List<Trip> significantTrips = filterSignificantTrips(allTripsInRange);
        for (Trip trip : significantTrips) {
            MemoryBlock tripBlock = new MemoryBlock(memory.getId(), BlockType.TRIP, position++);
            blocks.add(tripBlock);
        }
        
        log.info("Generated {} memory blocks", blocks.size());
        
        return blocks;
    }
    
    /**
     * Step 1: Find accommodation by analyzing visits during sleeping hours (22:00 - 06:00)
     */
    private ProcessedVisit findAccommodation(List<ProcessedVisit> visits) {
        Map<Long, Long> sleepingHoursDuration = new HashMap<>();
        
        for (ProcessedVisit visit : visits) {
            long durationInSleepingHours = calculateSleepingHoursDuration(visit);
            if (durationInSleepingHours > 0) {
                sleepingHoursDuration.merge(visit.getPlace().getId(), durationInSleepingHours, Long::sum);
            }
        }
        
        return sleepingHoursDuration.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .flatMap(entry -> visits.stream()
                .filter(visit -> visit.getPlace().getId().equals(entry.getKey()))
                .findFirst())
            .orElse(null);
    }
    
    private long calculateSleepingHoursDuration(ProcessedVisit visit) {
        ZoneId timeZone = visit.getPlace().getTimezone();
        if (timeZone == null) {
            timeZone = ZoneId.systemDefault();
        }
        
        var startLocal = visit.getStartTime().atZone(timeZone);
        var endLocal = visit.getEndTime().atZone(timeZone);
        
        long totalSleepingDuration = 0;
        
        var currentDay = startLocal.toLocalDate();
        var lastDay = endLocal.toLocalDate();
        
        while (!currentDay.isAfter(lastDay)) {
            var sleepStart = currentDay.atTime(22, 0).atZone(timeZone);
            var sleepEnd = currentDay.plusDays(1).atTime(6, 0).atZone(timeZone);
            
            var overlapStart = sleepStart.isAfter(startLocal) ? sleepStart : startLocal;
            var overlapEnd = sleepEnd.isBefore(endLocal) ? sleepEnd : endLocal;
            
            if (overlapStart.isBefore(overlapEnd)) {
                totalSleepingDuration += Duration.between(overlapStart, overlapEnd).getSeconds();
            }
            
            currentDay = currentDay.plusDays(1);
        }
        
        return totalSleepingDuration;
    }
    
    /**
     * Step 1: Filter visits - remove accommodation and short visits
     */
    private List<ProcessedVisit> filterVisits(List<ProcessedVisit> visits, ProcessedVisit accommodation) {
        Long accommodationPlaceId = accommodation != null ? accommodation.getPlace().getId() : null;
        
        return visits.stream()
            .filter(visit -> {
                // Remove accommodation stays
                if (accommodationPlaceId != null && visit.getPlace().getId().equals(accommodationPlaceId)) {
                    return false;
                }
                
                // Remove very short visits
                if (visit.getDurationSeconds() < MIN_VISIT_DURATION_SECONDS) {
                    return false;
                }
                
                return true;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Step 3: Score visits based on duration, distance from accommodation, category, and novelty
     */
    private List<ScoredVisit> scoreVisits(List<ProcessedVisit> visits, ProcessedVisit accommodation) {
        // Count visit frequency for novelty calculation
        Map<Long, Long> visitCounts = visits.stream()
            .collect(Collectors.groupingBy(v -> v.getPlace().getId(), Collectors.counting()));
        
        // Calculate max duration for normalization
        long maxDuration = visits.stream()
            .mapToLong(ProcessedVisit::getDurationSeconds)
            .max()
            .orElse(1);
        
        return visits.stream()
            .map(visit -> {
                double score = 0.0;
                
                // Duration score (normalized 0-1)
                double durationScore = (double) visit.getDurationSeconds() / maxDuration;
                score += WEIGHT_DURATION * durationScore;
                
                // Distance from accommodation score
                if (accommodation != null) {
                    double distance = calculateDistance(
                        visit.getPlace().getLatitudeCentroid(),
                        visit.getPlace().getLongitudeCentroid(),
                        accommodation.getPlace().getLatitudeCentroid(),
                        accommodation.getPlace().getLongitudeCentroid()
                    );
                    // Normalize distance (assume max interesting distance is 50km)
                    double distanceScore = Math.min(distance / 50000.0, 1.0);
                    score += WEIGHT_DISTANCE * distanceScore;
                }
                
                // Category score
                double categoryScore = getCategoryWeight(visit.getPlace().getType());
                score += WEIGHT_CATEGORY * categoryScore;
                
                // Novelty score (inverse of visit count, normalized)
                long visitCount = visitCounts.get(visit.getPlace().getId());
                double noveltyScore = 1.0 / visitCount;
                score += WEIGHT_NOVELTY * noveltyScore;
                
                return new ScoredVisit(visit, score);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get category weight based on place type
     */
    private double getCategoryWeight(String placeType) {
        if (placeType == null) {
            return 0.3;
        }
        
        String type = placeType.toLowerCase();
        
        // High interest categories
        if (type.contains("museum") || type.contains("landmark") || type.contains("park") ||
            type.contains("tourist") || type.contains("historic") || type.contains("monument") ||
            type.contains("attraction")) {
            return 1.0;
        }
        
        // Medium interest categories
        if (type.contains("restaurant") || type.contains("cafe") || type.contains("shopping") ||
            type.contains("market") || type.contains("gallery") || type.contains("theater")) {
            return 0.6;
        }
        
        // Low interest categories
        if (type.contains("grocery") || type.contains("pharmacy") || type.contains("gas") ||
            type.contains("station") || type.contains("atm") || type.contains("bank")) {
            return 0.2;
        }
        
        // Default medium-low
        return 0.4;
    }
    
    /**
     * Calculate distance between two points using Haversine formula
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth's radius in meters
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * Step 4: Cluster visits using spatio-temporal proximity (simplified DBSCAN-like approach)
     */
    private List<VisitCluster> clusterVisits(List<ScoredVisit> scoredVisits) {
        if (scoredVisits.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Sort by time
        List<ScoredVisit> sortedVisits = new ArrayList<>(scoredVisits);
        sortedVisits.sort(Comparator.comparing(sv -> sv.getVisit().getStartTime()));
        
        List<VisitCluster> clusters = new ArrayList<>();
        VisitCluster currentCluster = new VisitCluster();
        currentCluster.addVisit(sortedVisits.get(0));
        
        for (int i = 1; i < sortedVisits.size(); i++) {
            ScoredVisit current = sortedVisits.get(i);
            ScoredVisit previous = sortedVisits.get(i - 1);
            
            long timeDiff = Duration.between(
                previous.getVisit().getEndTime(),
                current.getVisit().getStartTime()
            ).getSeconds();
            
            double distance = calculateDistance(
                previous.getVisit().getPlace().getLatitudeCentroid(),
                previous.getVisit().getPlace().getLongitudeCentroid(),
                current.getVisit().getPlace().getLatitudeCentroid(),
                current.getVisit().getPlace().getLongitudeCentroid()
            );
            
            // Check if current visit should be added to current cluster
            if (timeDiff <= CLUSTER_TIME_THRESHOLD_SECONDS && distance <= CLUSTER_DISTANCE_THRESHOLD_METERS) {
                currentCluster.addVisit(current);
            } else {
                // Start new cluster
                clusters.add(currentCluster);
                currentCluster = new VisitCluster();
                currentCluster.addVisit(current);
            }
        }
        
        // Add the last cluster
        if (!currentCluster.getVisits().isEmpty()) {
            clusters.add(currentCluster);
        }
        
        return clusters;
    }
    
    /**
     * Filter trips to only include significant ones (e.g., long distance, long duration)
     */
    private List<Trip> filterSignificantTrips(List<Trip> trips) {
        return trips.stream()
            .filter(trip -> {
                // Include trips longer than 30 minutes
                if (trip.getDurationSeconds() != null && trip.getDurationSeconds() > 1800) {
                    return true;
                }
                
                // Include trips longer than 5km
                if (trip.getEstimatedDistanceMeters() != null && trip.getEstimatedDistanceMeters() > 5000) {
                    return true;
                }
                
                return false;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Helper class to hold a visit with its calculated score
     */
    private static class ScoredVisit {
        private final ProcessedVisit visit;
        private final double score;
        
        public ScoredVisit(ProcessedVisit visit, double score) {
            this.visit = visit;
            this.score = score;
        }
        
        public ProcessedVisit getVisit() {
            return visit;
        }
        
        public double getScore() {
            return score;
        }
    }
    
    /**
     * Helper class to represent a cluster of visits
     */
    private static class VisitCluster {
        private final List<ScoredVisit> visits = new ArrayList<>();
        
        public void addVisit(ScoredVisit visit) {
            visits.add(visit);
        }
        
        public List<ScoredVisit> getVisits() {
            return visits;
        }
        
        public ScoredVisit getHighestScoredVisit() {
            return visits.stream()
                .max(Comparator.comparingDouble(ScoredVisit::getScore))
                .orElse(null);
        }
        
        public Instant getStartTime() {
            return visits.stream()
                .map(sv -> sv.getVisit().getStartTime())
                .min(Instant::compareTo)
                .orElse(null);
        }
        
        public Instant getEndTime() {
            return visits.stream()
                .map(sv -> sv.getVisit().getEndTime())
                .max(Instant::compareTo)
                .orElse(null);
        }
    }
}
