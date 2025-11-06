package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
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
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MemoryBlockGenerationService {
    private static final Logger log = LoggerFactory.getLogger(MemoryBlockGenerationService.class);

    private static final DateTimeFormatter FULL_DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final long MIN_VISIT_DURATION_SECONDS = 600;

    private static final double WEIGHT_DURATION = 1.0;
    private static final double WEIGHT_DISTANCE = 2.0;
    private static final double WEIGHT_CATEGORY = 3.0;
    private static final double WEIGHT_NOVELTY = 1.5;
    
    private static final long CLUSTER_TIME_THRESHOLD_SECONDS = 7200;
    private static final double CLUSTER_DISTANCE_THRESHOLD_METERS = 1000;
    
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final I18nService i18n;
    private final ImmichIntegrationService immichIntegrationService;
    private final StorageService storageService;
    private final HomeDetectionService homeDetectionService;

    public MemoryBlockGenerationService(ProcessedVisitJdbcService processedVisitJdbcService,
                                        TripJdbcService tripJdbcService,
                                        I18nService i18nService,
                                        ImmichIntegrationService immichIntegrationService,
                                        StorageService storageService, HomeDetectionService homeDetectionService) {
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.i18n = i18nService;
        this.immichIntegrationService = immichIntegrationService;
        this.storageService = storageService;
        this.homeDetectionService = homeDetectionService;
    }

    public List<MemoryBlockPart> generate(User user, Memory memory, ZoneId timeZone) {
        Instant startDate = memory.getStartDate();
        Instant endDate = memory.getEndDate();

        List<ProcessedVisit> allVisitsInRange = this.processedVisitJdbcService.findByUserAndTimeOverlap(user, startDate, endDate);
        List<Trip> allTripsInRange = this.tripJdbcService.findByUserAndTimeOverlap(user, startDate, endDate);
        
        // Step 1: Data Pre-processing & Filtering
        Optional<ProcessedVisit> accommodation = homeDetectionService.findAccommodation(allVisitsInRange, startDate, endDate);
        Optional<ProcessedVisit> homeBefore = homeDetectionService.findAccommodation(this.processedVisitJdbcService.findByUserAndTimeOverlap(user, startDate.minus(7, ChronoUnit.DAYS), startDate), startDate.minus(7, ChronoUnit.DAYS), startDate);
        Optional<ProcessedVisit> homeAfter = homeDetectionService.findAccommodation(this.processedVisitJdbcService.findByUserAndTimeOverlap(user, endDate, endDate.plus(7, ChronoUnit.DAYS)), endDate, endDate.plus(7, ChronoUnit.DAYS));

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
        scoredVisits.sort(Comparator.comparingDouble(ScoredVisit::score).reversed());

        log.info("Scored {} visits, top score: {}", scoredVisits.size(),
                scoredVisits.isEmpty() ? 0 : scoredVisits.getFirst().score());
        
        // Step 4: Clustering & Creating a Narrative
        List<VisitCluster> clusters = clusterVisits(scoredVisits);
        
        log.info("Created {} clusters from visits", clusters.size());
        
        // Generate memory block parts from clusters
        List<MemoryBlockPart> blockParts = new ArrayList<>();
        
        // Add introduction text block
        if (!clusters.isEmpty() && accommodation.isPresent() && homeBefore.isPresent()) {
            String introText = generateIntroductionText(memory, clusters, accommodation.orElse(null), homeBefore.orElse(null), startDate, endDate, timeZone);
            MemoryBlockText introBlock = new MemoryBlockText(null, i18n.translate("memory.generator.headline.text"), introText);
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
                String formattedStartDate = formatTime(tripsToAccommodation.getFirst().getStartTime(), timeZone);
                String formattedEndDate = formatTime(tripsToAccommodation.getLast().getEndTime(), timeZone);
                String text = i18n.translate("memory.generator.travel_to_accommodation.text",
                        homeBefore.map(h -> h.getPlace().getCity()).orElse(""),
                        formattedStartDate,
                        accommodation.map(a -> a.getPlace().getCity()).orElse(""),
                        formattedEndDate,
                        i18n.humanizeDuration(Duration.between(tripsToAccommodation.getFirst().getStartTime(), tripsToAccommodation.getLast().getEndTime())),
                        i18n.humanizeDuration(tripsToAccommodation.stream().map(Trip::getDurationSeconds).reduce(0L, Long::sum))
                );

                MemoryBlockText accommodationPreRoll = new MemoryBlockText(null, null, text);
                blockParts.add(accommodationPreRoll);
            }

            MemoryClusterBlock clusterBlock = convertToTripCluster(tripsToAccommodation, "Journey to " + accommodation.get().getPlace().getCity());
            blockParts.add(clusterBlock);
        }

        Map<LocalDate, List<PhotoResponse>> imagesByDay = loadImagesFromIntegrations(user, startDate, endDate);

        accommodation.ifPresent(a -> {
            MemoryBlockText intro = new MemoryBlockText(null,
                    i18n.translate("memory.generator.intro_accommodation.headline",a.getPlace().getName()),
                    i18n.translate("memory.generator.intro_accommodation.text"));
            blockParts.add(intro);
            MemoryClusterBlock clusterBlock = new MemoryClusterBlock(null, List.of(a.getId()), null, null, BlockType.CLUSTER_VISIT);
            blockParts.add(clusterBlock);
            LocalDate dayOfAccommodation = a.getStartTime().atZone(ZoneId.of("UTC")).toLocalDate();
            List<PhotoResponse> images = imagesByDay.get(dayOfAccommodation);
            if (images != null && !images.isEmpty()) {
                MemoryBlockImageGallery imageGallery = new MemoryBlockImageGallery(null, fetchImagesFromImmich(user, memory, images));
                blockParts.add(imageGallery);
                imagesByDay.remove(dayOfAccommodation);
            }
        });

        Set<LocalDate> handledDays = new HashSet<>();

        // Process each cluster
        ProcessedVisit previousVisit = accommodation.orElse(null);
        boolean firstOfDay;
        for (int i = 0; i < clusters.size(); i++) {

            VisitCluster cluster = clusters.get(i);
            LocalDate today = cluster.getStartTime().atZone(ZoneId.systemDefault()).toLocalDate();


            //filter out visits before the first stay at accommodation
            if (firstAccommodationArrival != null && cluster.getEndTime() != null && cluster.getEndTime().isBefore(firstAccommodationArrival)) {
                continue;
            }
            //filter out visits after the last stay at accommodation
            if (lastAccommodationDeparture != null && cluster.getStartTime() != null && cluster.getStartTime().isAfter(lastAccommodationDeparture)) {
                continue;
            }

            if (!handledDays.contains(today)) {
                blockParts.add(new MemoryBlockText(null, i18n.translate("memory.generator.day.text",
                        Duration.between(startDate.truncatedTo(ChronoUnit.DAYS), cluster.getStartTime().truncatedTo(ChronoUnit.DAYS)).toDays(), cluster.getHighestScoredVisit().visit.getPlace().getCity()), null));
                handledDays.add(today);
                firstOfDay = true;
            } else {
                firstOfDay = false;
            }
            if (previousVisit != null) {
                ProcessedVisit finalPreviousVisit = previousVisit;
                List<Trip> tripsBetweenVisits = allTripsInRange.stream()
                        .filter(trip -> trip.getStartTime() != null && (trip.getStartTime().equals(finalPreviousVisit.getEndTime()) || trip.getStartTime().isAfter(finalPreviousVisit.getEndTime())))
                        .filter(trip -> trip.getEndTime() != null && (trip.getEndTime().equals(cluster.getStartTime())))
                        .sorted(Comparator.comparing(Trip::getEndTime))
                        .toList();
                if (!tripsBetweenVisits.isEmpty() && cluster.getHighestScoredVisit() != null) {
                    if (Duration.between(tripsBetweenVisits.getFirst().getStartTime(), tripsBetweenVisits.getLast().getEndTime()).toMinutes() > 30) {
                        MemoryClusterBlock clusterBlock = convertToTripCluster(tripsBetweenVisits, i18n.translate("memory.generator.journey_to.headline.text", cluster.getHighestScoredVisit().visit().getPlace().getCity()));
                        blockParts.add(clusterBlock);
                    }
                }
                previousVisit = cluster.getVisits().stream().map(ScoredVisit::visit).max(Comparator.comparing(ProcessedVisit::getEndTime)).orElse(null);
            }

            // Add a text block describing the cluster
            String clusterHeadline = firstOfDay ? null : generateClusterHeadline(cluster, i + 1);
            MemoryBlockText clusterTextBlock = new MemoryBlockText(null, clusterHeadline, null);
            blockParts.add(clusterTextBlock);


            MemoryClusterBlock clusterBlock = new MemoryClusterBlock(null, cluster.getVisits().stream().map(ScoredVisit::visit)
                    .map(ProcessedVisit::getId).toList(), null, null, BlockType.CLUSTER_VISIT);
            blockParts.add(clusterBlock);

            List<PhotoResponse> todaysImages = imagesByDay.getOrDefault(today, Collections.emptyList());
            if (!todaysImages.isEmpty()) {
                MemoryBlockImageGallery imageGallery = new MemoryBlockImageGallery(null, fetchImagesFromImmich(user, memory, todaysImages));
                blockParts.add(imageGallery);
            }
            imagesByDay.remove(today);
        }
        
        if (lastAccommodationDeparture != null) {
            List<Trip> tripsFromAccommodation = allTripsInRange.stream()
                    .filter(trip -> trip.getStartTime() != null && !trip.getStartTime().isBefore(lastAccommodationDeparture))
                    .filter(trip -> trip.getEndTime() != null && !trip.getEndTime().isAfter(endDate))
                    .sorted(Comparator.comparing(Trip::getStartTime))
                    .toList();


            if (!tripsFromAccommodation.isEmpty()) {
                String formattedStartDate = formatTime(tripsFromAccommodation.getFirst().getStartTime(), timeZone);
                String formattedEndDate = formatTime(tripsFromAccommodation.getLast().getEndTime(), timeZone);
                String text = i18n.translate("memory.generator.travel_from_accommodation.text",
                        accommodation.map(a -> a.getPlace().getCity()).orElse(""),
                        formattedStartDate,
                        homeAfter.map(h -> h.getPlace().getCity()).orElse(""),
                        formattedEndDate,
                        i18n.humanizeDuration(Duration.between(tripsFromAccommodation.getFirst().getStartTime(), tripsFromAccommodation.getLast().getEndTime())),
                        i18n.humanizeDuration(tripsFromAccommodation.stream().map(Trip::getDurationSeconds).reduce(0L, Long::sum))
                );

                MemoryBlockText accommodationPreRoll = new MemoryBlockText(null, null, text);
                blockParts.add(accommodationPreRoll);
            }


            if (homeAfter.isPresent()) {
                MemoryClusterBlock clusterBlock = convertToTripCluster(tripsFromAccommodation, "Journey to " + homeAfter.get().getPlace().getCity());
                blockParts.add(clusterBlock);
            }
        }

        log.info("Generated {} memory block parts", blockParts.size());

        return blockParts;
    }

    private List<MemoryBlockImageGallery.GalleryImage> fetchImagesFromImmich(User user, Memory memory, List<PhotoResponse> todaysImages) {
        return todaysImages.stream()
                .map(s -> {
                    if (storageService.exists("memories/" + memory.getId() + "/" + s + "**")) {
                        return new MemoryBlockImageGallery.GalleryImage("/api/v1/photos/reitti/memories/" + memory.getId() + "/" + s.getFileName(), null, "immich", s.getId());
                    } else {
                        String filename = this.immichIntegrationService.downloadImage(user, s.getId(), "memories/" + memory.getId());
                        String imageUrl = "/api/v1/photos/reitti/memories/" + memory.getId() + "/" + filename;
                        return new MemoryBlockImageGallery.GalleryImage(imageUrl, null, "immich", s.getId());
                    }
                }).toList();
    }

    private Map<LocalDate, List<PhotoResponse>> loadImagesFromIntegrations(User user, Instant startDate, Instant endDate) {
        Map<LocalDate, List<PhotoResponse>> map = new HashMap<>();
        LocalDate currentStart = startDate.atZone(ZoneId.of("UTC")).toLocalDate();
        LocalDate currentEnd = startDate.plus(1, ChronoUnit.DAYS).atZone(ZoneId.of("UTC")).toLocalDate();
        LocalDate end = endDate.atZone(ZoneId.of("UTC")).toLocalDate();
        while (!currentEnd.isAfter(end)) {
            map.put(currentStart, this.immichIntegrationService.searchPhotosForRange(user, currentStart, currentStart, "UTC")
                    .stream().sorted(Comparator.comparing(PhotoResponse::getDateTime)).toList());

            currentStart = currentEnd;
            currentEnd = currentEnd.plusDays(1);
        }
        return map;
    }

    private MemoryClusterBlock convertToTripCluster(List<Trip> trips, String title) {
        return new MemoryClusterBlock(null, trips.stream().map(Trip::getId).toList(),
                title, null, BlockType.CLUSTER_TRIP);
    }

    private String generateIntroductionText(Memory memory, List<VisitCluster> clusters, ProcessedVisit accommodation, ProcessedVisit homePlace,
                                            Instant startDate, Instant endDate, ZoneId timeZone) {
        long totalDays = Duration.between(memory.getStartDate(), memory.getEndDate()).toDays() + 1;
        int totalVisits = clusters.stream().mapToInt(c -> c.getVisits().size()).sum();
        SignificantPlace accommodationPlace = accommodation.getPlace();
        String country;
        if (accommodationPlace.getCountryCode() != null) {
            country = i18n.translateWithDefault("country." + accommodationPlace.getCountryCode() + ".label", accommodation.getPlace().getCountryCode().toLowerCase());
        } else {
            country = i18n.translate("country.unknown.label");
        }
        String formattedStartDate = formatDate(startDate, timeZone, true);
        String formattedEndDate = formatDate(endDate, timeZone, false);

        return i18n.translate("memory.generator.introductory.text",
                        formattedStartDate,
                        homePlace.getPlace().getCity(),
                        totalDays,
                        accommodationPlace.getCity(),
                        country,
                        totalVisits,
                        clusters.size(),
                        formattedEndDate);
    }

    private static String formatDate(Instant date, ZoneId timeZone, boolean withYear) {
        LocalDate localEnd = date.atZone(timeZone).toLocalDate();
        return withYear ? localEnd.format(FULL_DATE_FORMATTER.withLocale(LocaleContextHolder.getLocale())) : localEnd.format(DATE_FORMATTER.withLocale(LocaleContextHolder.getLocale()));
    }

    private static String formatTime(Instant date, ZoneId timeZone) {
        LocalDateTime localEnd = date.atZone(timeZone).toLocalDateTime();
        return localEnd.format(TIME_FORMATTER.withLocale(LocaleContextHolder.getLocale()));
    }

    /**
     * Generate a headline for a visit cluster
     */
    private String generateClusterHeadline(VisitCluster cluster, int clusterNumber) {
        ScoredVisit topVisit = cluster.getHighestScoredVisit();
        if (topVisit != null && topVisit.visit().getPlace().getName() != null) {
            return topVisit.visit().getPlace().getName();
        }
        return "Location " + clusterNumber;
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

                return visit.getDurationSeconds() >= MIN_VISIT_DURATION_SECONDS;
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
                    double distance = GeoUtils.distanceInMeters(
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
     * Step 4: Cluster visits using spatio-temporal proximity (simplified DBSCAN-like approach)
     */
    private List<VisitCluster> clusterVisits(List<ScoredVisit> scoredVisits) {
        if (scoredVisits.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Sort by time
        List<ScoredVisit> sortedVisits = new ArrayList<>(scoredVisits);
        sortedVisits.sort(Comparator.comparing(sv -> sv.visit().getStartTime()));
        
        List<VisitCluster> clusters = new ArrayList<>();
        VisitCluster currentCluster = new VisitCluster();
        currentCluster.addVisit(sortedVisits.getFirst());
        
        for (int i = 1; i < sortedVisits.size(); i++) {
            ScoredVisit current = sortedVisits.get(i);
            ScoredVisit previous = sortedVisits.get(i - 1);
            
            long timeDiff = Duration.between(
                    previous.visit().getEndTime(),
                    current.visit().getStartTime()
            ).getSeconds();

            double distance = GeoUtils.distanceInMeters(
                    previous.visit().getPlace().getLatitudeCentroid(),
                    previous.visit().getPlace().getLongitudeCentroid(),
                    current.visit().getPlace().getLatitudeCentroid(),
                    current.visit().getPlace().getLongitudeCentroid()
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

    private record ScoredVisit(ProcessedVisit visit, double score) {
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
                    .max(Comparator.comparingDouble(ScoredVisit::score))
                .orElse(null);
        }
        
        public Instant getStartTime() {
            return visits.stream()
                    .map(sv -> sv.visit().getStartTime())
                .min(Instant::compareTo)
                .orElse(null);
        }
        
        public Instant getEndTime() {
            return visits.stream()
                    .map(sv -> sv.visit().getEndTime())
                .max(Instant::compareTo)
                .orElse(null);
        }
    }
}
