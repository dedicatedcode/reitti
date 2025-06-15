package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.VisitRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatisticsService {
    
    @Autowired
    private VisitRepository visitRepository;
    
    @Autowired
    private TripRepository tripRepository;
    
    @Autowired
    private ProcessedVisitRepository processedVisitRepository;
    
    @Autowired
    private RawLocationPointRepository rawLocationPointRepository;

    private static TransportStatistic mapTransportStatistics(Object[] row) {
        String transportMode = (String) row[0];
        Double totalDistanceMeters = (Double) row[1];
        Long durationInSeconds = (Long) row[2];
        Long tripCount = (Long) row[3];

        double totalDistanceKm = totalDistanceMeters / 1000.0;
        double totalDurationHours = durationInSeconds / 3600.0;

        return new TransportStatistic(
                transportMode != null ? transportMode : "unknown",
                totalDistanceKm,
                totalDurationHours,
                tripCount.intValue()
        );
    }

    public List<Integer> getAvailableYears(User user) {
        return rawLocationPointRepository.findDistinctYearsByUser(user);
    }
    
    public static class VisitStatistic {
        private final String placeName;
        private final long totalStayTimeHours;
        private final int visitCount;
        
        public VisitStatistic(String placeName, long totalStayTimeHours, int visitCount) {
            this.placeName = placeName;
            this.totalStayTimeHours = totalStayTimeHours;
            this.visitCount = visitCount;
        }
        
        public String getPlaceName() { return placeName; }
        public long getTotalStayTimeHours() { return totalStayTimeHours; }
        public int getVisitCount() { return visitCount; }
    }
    
    public static class TransportStatistic {
        private final String transportMode;
        private final double totalDistanceKm;
        private final int tripCount;
        private final double totalDurationHours;

        public TransportStatistic(String transportMode, double totalDistanceKm, double totalDurationHours, int tripCount) {
            this.transportMode = transportMode;
            this.totalDistanceKm = totalDistanceKm;
            this.totalDurationHours = totalDistanceKm;
            this.tripCount = tripCount;
        }
        
        public String getTransportMode() { return transportMode; }
        public double getTotalDistanceKm() { return totalDistanceKm; }
        public int getTripCount() { return tripCount; }
        public double getTotalDurationHours() { return totalDurationHours; }
    }
    
    public List<VisitStatistic> getTopVisitsByStayTime(User user, Instant startTime, Instant endTime, int limit) {
        List<Object[]> results;
        if (startTime == null || endTime == null) {
             results = processedVisitRepository.findTopPlacesByStayTimeWithLimit(user, limit);
        } else {
            results = processedVisitRepository.findTopPlacesByStayTimeWithLimit(user, startTime, endTime, limit);
        }

        return results.stream()
                .map(row -> {
                    String placeName = (String) row[0];
                    Long totalDurationSeconds = (Long) row[1];
                    Long visitCount = (Long) row[2];
                    
                    // Convert seconds to hours
                    long totalStayTimeHours = totalDurationSeconds / 3600;
                    return new VisitStatistic(
                        placeName != null ? placeName : "Unknown Place",
                        totalStayTimeHours,
                        visitCount.intValue()
                    );
                })
                .collect(Collectors.toList());
    }
    
    public List<TransportStatistic> getTransportStatistics(User user, Instant startTime, Instant endTime) {
        List<Object[]> results = tripRepository.findTransportStatisticsByUserAndTimeRange(user, startTime, endTime);
        
        return results.stream()
                .map(StatisticsService::mapTransportStatistics)
                .collect(Collectors.toList());
    }

    public List<TransportStatistic> getTransportStatistics(User user) {
        List<Object[]> results = tripRepository.findTransportStatisticsByUser(user);
        
        return results.stream()
                .map(StatisticsService::mapTransportStatistics)
                .collect(Collectors.toList());
    }
    
    public List<VisitStatistic> getOverallTopVisits(User user) {
        return getTopVisitsByStayTime(user, null, null, 5);
    }
    
    public List<TransportStatistic> getOverallTransportStatistics(User user) {
        return getTransportStatistics(user);
    }
    
    public List<VisitStatistic> getYearTopVisits(User user, int year) {
        Instant startOfYear = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfYear = LocalDate.of(year, 12, 31).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        return getTopVisitsByStayTime(user, startOfYear, endOfYear, 5);
    }
    
    public List<TransportStatistic> getYearTransportStatistics(User user, int year) {
        Instant startOfYear = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfYear = LocalDate.of(year, 12, 31).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        return getTransportStatistics(user, startOfYear, endOfYear);
    }
    
    public List<VisitStatistic> getMonthTopVisits(User user, int year, int month) {
        Instant startOfMonth = LocalDate.of(year, month, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfMonth = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        return getTopVisitsByStayTime(user, startOfMonth, endOfMonth, 5);
    }
    
    public List<TransportStatistic> getMonthTransportStatistics(User user, int year, int month) {
        Instant startOfMonth = LocalDate.of(year, month, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfMonth = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        return getTransportStatistics(user, startOfMonth, endOfMonth);
    }
}
