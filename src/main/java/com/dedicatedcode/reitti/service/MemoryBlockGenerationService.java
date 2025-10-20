package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MemoryBlockGenerationService {
    private static final Logger log = LoggerFactory.getLogger(MemoryBlockGenerationService.class);
    
    private static final long MIN_VISIT_DURATION_SECONDS = 600;
    
    private static final double WEIGHT_DURATION = 1.0;
    private static final double WEIGHT_DISTANCE = 2.0;
    private static final double WEIGHT_CATEGORY = 3.0;
    private static final double WEIGHT_NOVELTY = 1.5;
    
    private static final long CLUSTER_TIME_THRESHOLD_SECONDS = 7200;
    private static final double CLUSTER_DISTANCE_THRESHOLD_METERS = 1000;
    
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final MessageSource messageSource;
    private final ImmichIntegrationService immichIntegrationService;

    public MemoryBlockGenerationService(ProcessedVisitJdbcService processedVisitJdbcService, TripJdbcService tripJdbcService, MessageSource messageSource, ImmichIntegrationService immichIntegrationService) {
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.messageSource = messageSource;
        this.immichIntegrationService = immichIntegrationService;
    }

    public List<MemoryBlockPart> generate(User user, Memory memory) {
        Instant startDate = memory.getStartDate();
        Instant endDate = memory.getEndDate();

        List<ProcessedVisit> allVisitsInRange = this.processedVisitJdbcService.findByUserAndTimeOverlap(user, startDate, endDate);
        List<Trip> allTripsInRange = this.tripJdbcService.findByUserAndTimeOverlap(user, startDate, endDate);
        
        // Step 1: Data Pre-processing & Filtering
        Optional<ProcessedVisit> accommodation = findAccommodation(allVisitsInRange);
        Optional<ProcessedVisit> home = findHome(allVisitsInRange);

        // Find first and last accommodation visits
        Instant firstAccommodationArrival = accommodation.flatMap(p -> allVisitsInRange.stream()
                        .filter(visit -> visit.getPlace().getId().equals(p.getPlace().getId()))
                        .min(Comparator.comparing(ProcessedVisit::getStartTime)))
                        .map(ProcessedVisit::getStartTime).orElse(null);
        Instant lastAccommodationDeparture = accommodation.flatMap(p -> allVisitsInRange.stream()
                        .filter(visit -> visit.getPlace().getId().equals(p.getPlace().getId()))
                        .max(Comparator.comparing(ProcessedVisit::getStartTime)))
                        .map(ProcessedVisit::getStartTime).orElse(null);

        List<ProcessedVisit> filteredVisits = accommodation.map(a -> filterVisits(allVisitsInRange, a)).orElse(allVisitsInRange);
        
        log.info("Found {} visits after filtering (accommodation: {})", filteredVisits.size(), accommodation.map(a -> a.getPlace().getName()).orElse("none"));
        
        // Step 3: Scoring & Identifying "Interesting" Visits
        List<ScoredVisit> scoredVisits = scoreVisits(filteredVisits, accommodation.orElse(null));
        
        // Sort by score descending
        scoredVisits.sort(Comparator.comparingDouble(ScoredVisit::getScore).reversed());
        
        log.info("Scored {} visits, top score: {}", scoredVisits.size(), 
                scoredVisits.isEmpty() ? 0 : scoredVisits.getFirst().getScore());
        
        // Step 4: Clustering & Creating a Narrative
        List<VisitCluster> clusters = clusterVisits(scoredVisits);
        
        log.info("Created {} clusters from visits", clusters.size());
        
        // Generate memory block parts from clusters
        List<MemoryBlockPart> blockParts = new ArrayList<>();
        
        // Add introduction text block
        if (!clusters.isEmpty() && accommodation.isPresent() && home.isPresent() && startDate != null && endDate != null) {
            String introText = generateIntroductionText(memory, clusters, accommodation.orElse(null), home.orElse(null), startDate, endDate);
            MemoryBlockText introBlock = new MemoryBlockText(null, "Your Journey", introText);
            blockParts.add(introBlock);
        }

        // Add travel to the accommodation section
        if (firstAccommodationArrival != null) {
            List<Trip> tripsToAccommodation = allTripsInRange.stream()
                .filter(trip -> trip.getEndTime() != null && !trip.getEndTime().isAfter(firstAccommodationArrival))
                .filter(trip -> trip.getStartTime() != null && !trip.getStartTime().isBefore(startDate))
                .sorted(Comparator.comparing(Trip::getStartTime))
                .toList();

            if (!tripsToAccommodation.isEmpty()) {
                String text = messageSource.getMessage("memory.generator.travel_to_accommodation.text", new Object[]{
                        home.map(h ->h.getPlace().getCity()).orElse(""),
                        tripsToAccommodation.getFirst().getStartTime(),
                        accommodation.map(a -> a.getPlace().getCity()).orElse(""),
                        tripsToAccommodation.getLast().getEndTime(),
                        Duration.between(tripsToAccommodation.getFirst().getStartTime(), tripsToAccommodation.getLast().getEndTime()).toSeconds(),
                        tripsToAccommodation.stream().map(Trip::getDurationSeconds).reduce(0L, Long::sum)
                }, LocaleContextHolder.getLocale());

                MemoryBlockText accommodationPreRoll = new MemoryBlockText(null, null, text);
                blockParts.add(accommodationPreRoll);
            }


            MemoryClusterBlock clusterBlock = convertToClusterBlock(tripsToAccommodation, accommodation.get());
            blockParts.add(clusterBlock);
        }

        Map<LocalDate, List<String>> imagesByDay = loadImagesFromIntegrations(user, startDate, endDate);

        accommodation.ifPresent(a -> {
            MemoryBlockText intro = new MemoryBlockText(null, "Welcome to " + a.getPlace().getName(),
                    messageSource.getMessage("memory.generator.intro_accommodation.text", new Object[]{

            }, LocaleContextHolder.getLocale()));
            blockParts.add(intro);
            blockParts.add(convertVisitToBlock(a));
            LocalDate dayOfAccommodation = a.getStartTime().atZone(ZoneId.of("UTC")).toLocalDate();
            List<String> images = imagesByDay.get(dayOfAccommodation);
            if (images != null && !images.isEmpty()) {
                MemoryBlockImageGallery imageGallery = new MemoryBlockImageGallery(null, images.stream()
                        .map(createImageFromAssetId(user, memory)).toList());
                blockParts.add(imageGallery);
                imagesByDay.remove(dayOfAccommodation);
            }
        });

        // Process each cluster
        for (int i = 0; i < clusters.size(); i++) {
            VisitCluster cluster = clusters.get(i);

            //filter out visits before the first stay at accommodation
            if (firstAccommodationArrival != null && cluster.getEndTime() != null && cluster.getEndTime().isBefore(firstAccommodationArrival)) {
                continue;
            }
            //filter out visits after the last stay at accommodation
            if (lastAccommodationDeparture != null && cluster.getStartTime() != null && cluster.getStartTime().isAfter(lastAccommodationDeparture)) {
                continue;
            }

            // Add a text block describing the cluster
            String clusterHeadline = generateClusterHeadline(cluster, i + 1);
            String clusterDescription = generateClusterDescription(cluster);
            MemoryBlockText clusterTextBlock = new MemoryBlockText(null, clusterHeadline, clusterDescription);
            blockParts.add(clusterTextBlock);


            MemoryClusterBlock clusterBlock = new MemoryClusterBlock(null, cluster.getVisits().stream().map(ScoredVisit::getVisit)
                    .map(ProcessedVisit::getId).toList(), null, null, BlockType.CLUSTER_VISIT);
            blockParts.add(clusterBlock);

            LocalDate today = cluster.getStartTime().atZone(ZoneId.systemDefault()).toLocalDate();
            List<String> todaysImages = imagesByDay.getOrDefault(today, Collections.emptyList());
            if (!todaysImages.isEmpty()) {
                MemoryBlockImageGallery imageGallery = new MemoryBlockImageGallery(null, todaysImages.stream()
                        .map(createImageFromAssetId(user, memory)).toList());
                blockParts.add(imageGallery);
            }
            imagesByDay.remove(today);

            if (i < clusters.size() - 1) {
                VisitCluster nextCluster = clusters.get(i + 1);
                Optional<Trip> tripBetween = findTripBetweenClusters(allTripsInRange, cluster, nextCluster);
                if (tripBetween.isPresent()) {
                    MemoryBlockTrip tripBlock = convertTripToBlock(tripBetween.get());
                    blockParts.add(tripBlock);
                }
            }
        }
        
        if (lastAccommodationDeparture != null) {
            List<Trip> tripsFromAccommodation = allTripsInRange.stream()
                    .filter(trip -> trip.getStartTime() != null && !trip.getStartTime().isBefore(lastAccommodationDeparture))
                    .filter(trip -> trip.getEndTime() != null && !trip.getEndTime().isAfter(endDate))
                    .sorted(Comparator.comparing(Trip::getStartTime))
                    .toList();


            if (!tripsFromAccommodation.isEmpty()) {
                String text = messageSource.getMessage("memory.generator.travel_from_accommodation.text", new Object[]{
                        accommodation.map(a -> a.getPlace().getCity()).orElse(""),
                        tripsFromAccommodation.getFirst().getStartTime(),
                        home.map(h -> h.getPlace().getCity()).orElse(""),
                        tripsFromAccommodation.getLast().getEndTime(),
                        Duration.between(tripsFromAccommodation.getFirst().getStartTime(), tripsFromAccommodation.getLast().getEndTime()).toSeconds(),
                        tripsFromAccommodation.stream().map(Trip::getDurationSeconds).reduce(0L, Long::sum)
                }, LocaleContextHolder.getLocale());

                MemoryBlockText accommodationPreRoll = new MemoryBlockText(null, null, text);
                blockParts.add(accommodationPreRoll);
            }


            MemoryClusterBlock clusterBlock = convertToClusterBlock(tripsFromAccommodation, home.get());

            blockParts.add(clusterBlock);
        }

        log.info("Generated {} memory block parts", blockParts.size());
        
        return blockParts;
    }

    private Function<String, MemoryBlockImageGallery.GalleryImage> createImageFromAssetId(User user, Memory memory) {
        return s -> {
            String filename = this.immichIntegrationService.downloadImage(user, s, "memories/" + memory.getId());
            String imageUrl = "/api/v1/photos/reitti/memories/" + memory.getId() + "/" + filename;
            return new MemoryBlockImageGallery.GalleryImage(imageUrl, null);
        };
    }

    private Map<LocalDate, List<String>> loadImagesFromIntegrations(User user, Instant startDate, Instant endDate) {
        Map<LocalDate, List<String>> map = new HashMap<>();
        LocalDate currentStart = startDate.atZone(ZoneId.of("UTC")).toLocalDate();
        LocalDate currentEnd = startDate.plus(1, ChronoUnit.DAYS).atZone(ZoneId.of("UTC")).toLocalDate();
        LocalDate end = endDate.atZone(ZoneId.of("UTC")).toLocalDate();
        while (!currentEnd.isAfter(end)) {
            map.put(currentStart, this.immichIntegrationService.searchPhotosForRange(user, currentStart, currentStart, "UTC")
                    .stream().map(PhotoResponse::getId).toList());

            currentStart = currentEnd;
            currentEnd = currentEnd.plusDays(1);
        }
        return map;
    }

    private Optional<ProcessedVisit> findHome(List<ProcessedVisit> allVisitsInRange) {
        if (allVisitsInRange.stream().findFirst().isPresent()) {
            boolean homeDetected =  allVisitsInRange.stream().findFirst().get().getPlace().equals(allVisitsInRange.getLast().getPlace());
            if (homeDetected) {
                return allVisitsInRange.stream().findFirst();
            }
        }
        return Optional.empty();
    }

    private MemoryClusterBlock convertToClusterBlock(List<Trip> tripsToAccommodation, ProcessedVisit accommodation) {
        return new MemoryClusterBlock(null, tripsToAccommodation.stream().map(Trip::getId).toList(),
                "Journey to " + accommodation.getPlace().getCity(), null, BlockType.CLUSTER_TRIP);
    }

    private MemoryBlockVisit convertVisitToBlock(ProcessedVisit visit) {
        return new MemoryBlockVisit(
            null, // blockId will be set when saved
            visit.getId(),
            visit.getPlace().getName(),
            visit.getPlace().getAddress(),
            visit.getPlace().getLatitudeCentroid(),
            visit.getPlace().getLongitudeCentroid(),
            visit.getStartTime(),
            visit.getEndTime(),
            visit.getDurationSeconds()
        );
    }
    
    /**
     * Convert a Trip to a MemoryBlockTrip with embedded data
     */
    private MemoryBlockTrip convertTripToBlock(Trip trip) {
        String startPlaceName = null;
        Double startLat = null;
        Double startLon = null;
        String endPlaceName = null;
        Double endLat = null;
        Double endLon = null;
        
        if (trip.getStartVisit() != null && trip.getStartVisit().getPlace() != null) {
            startPlaceName = trip.getStartVisit().getPlace().getName();
            startLat = trip.getStartVisit().getPlace().getLatitudeCentroid();
            startLon = trip.getStartVisit().getPlace().getLongitudeCentroid();
        }
        
        if (trip.getEndVisit() != null && trip.getEndVisit().getPlace() != null) {
            endPlaceName = trip.getEndVisit().getPlace().getName();
            endLat = trip.getEndVisit().getPlace().getLatitudeCentroid();
            endLon = trip.getEndVisit().getPlace().getLongitudeCentroid();
        }
        
        return new MemoryBlockTrip(
            null, // blockId will be set when saved
                trip.getStartTime(),
            trip.getEndTime(),
            trip.getDurationSeconds(),
            trip.getEstimatedDistanceMeters(),
            trip.getTravelledDistanceMeters(),
            trip.getTransportModeInferred(),
            startPlaceName,
            startLat,
            startLon,
            endPlaceName,
            endLat,
            endLon
        );
    }
    
    /**
     * Generate an introduction text for the memory
     */
    private String generateIntroductionText(Memory memory, List<VisitCluster> clusters, ProcessedVisit accommodation, ProcessedVisit homePlace,
                                            Instant startDate, Instant endDate) {
        long totalDays = Duration.between(memory.getStartDate(), memory.getEndDate()).toDays() + 1;
        int totalVisits = clusters.stream().mapToInt(c -> c.getVisits().size()).sum();
        SignificantPlace accommodationPlace = accommodation.getPlace();
        String country;
        if (accommodationPlace.getCountryCode() != null) {
            country = messageSource.getMessage("country." + accommodationPlace.getCountryCode() + ".label", null, accommodation.getPlace().getCountryCode().toLowerCase(), LocaleContextHolder.getLocale());
        } else {
            country = messageSource.getMessage("country.unknown.label", null, LocaleContextHolder.getLocale());
        }

        return messageSource.getMessage("memory.generator.introductory.text",
                new Object[]{startDate, homePlace.getPlace().getCity(),
                        totalDays,
                        accommodationPlace.getCity(),
                        country,
                        totalVisits,
                        clusters.size(),
                        endDate
                },
            LocaleContextHolder.getLocale());
    }
    
    /**
     * Generate a headline for a visit cluster
     */
    private String generateClusterHeadline(VisitCluster cluster, int clusterNumber) {
        ScoredVisit topVisit = cluster.getHighestScoredVisit();
        if (topVisit != null && topVisit.getVisit().getPlace().getName() != null) {
            return topVisit.getVisit().getPlace().getName();
        }
        return "Location " + clusterNumber;
    }
    
    /**
     * Generate a description for a visit cluster
     */
    private String generateClusterDescription(VisitCluster cluster) {
        StringBuilder description = new StringBuilder();
        
        Instant startTime = cluster.getStartTime();
        Instant endTime = cluster.getEndTime();
        
        if (startTime != null && endTime != null) {
            long durationHours = Duration.between(startTime, endTime).toHours();
            long durationMinutes = Duration.between(startTime, endTime).toMinutes() % 60;
            
            description.append("Spent ");
            if (durationHours > 0) {
                description.append(durationHours).append(" hour");
                if (durationHours != 1) {
                    description.append("s");
                }
            }
            if (durationMinutes > 0) {
                if (durationHours > 0) {
                    description.append(" and ");
                }
                description.append(durationMinutes).append(" minute");
                if (durationMinutes != 1) {
                    description.append("s");
                }
            }
            description.append(" exploring this area");
            
            if (cluster.getVisits().size() > 1) {
                description.append(", visiting ").append(cluster.getVisits().size()).append(" places");
            }
            
            description.append(".");
        }
        
        return description.toString();
    }
    
    /**
     * Find a trip that connects two clusters
     */
    private Optional<Trip> findTripBetweenClusters(List<Trip> allTrips, VisitCluster fromCluster, VisitCluster toCluster) {
        Instant fromEnd = fromCluster.getEndTime();
        Instant toStart = toCluster.getStartTime();
        
        if (fromEnd == null || toStart == null) {
            return Optional.empty();
        }
        
        return allTrips.stream()
            .filter(trip -> {
                Instant tripStart = trip.getStartTime();
                Instant tripEnd = trip.getEndTime();
                
                if (tripStart == null || tripEnd == null) {
                    return false;
                }
                
                // Trip should start after the first cluster ends and end before the second cluster starts
                return !tripStart.isBefore(fromEnd) && !tripEnd.isAfter(toStart);
            })
            .findFirst();
    }
    
    /**
     * Step 1: Find accommodation by analyzing visits during sleeping hours (22:00 - 06:00)
     */
    private Optional<ProcessedVisit> findAccommodation(List<ProcessedVisit> visits) {
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
                .findFirst());
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
                if (visit.getPlace().getId().equals(accommodationPlaceId)) {
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
    private double getCategoryWeight(SignificantPlace.PlaceType placeType) {
        if (placeType == null) {
            return 0.3;
        }
        
        // High interest categories
        return switch (placeType) {
            case MUSEUM, LANDMARK, PARK, TOURIST_ATTRACTION, HISTORIC_SITE, MONUMENT -> 1.0;
            // Medium interest categories
            case RESTAURANT, CAFE, SHOPPING_MALL, MARKET, GALLERY, THEATER, CINEMA -> 0.6;
            // Low interest categories
            case GROCERY_STORE, PHARMACY, GAS_STATION, ATM, BANK -> 0.2;
            // Default medium-low for all other types
            default -> 0.4;
        };
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
                return trip.getEstimatedDistanceMeters() != null && trip.getEstimatedDistanceMeters() > 5000;
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
