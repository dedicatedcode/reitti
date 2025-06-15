package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.VisitRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class StatisticsService {
    
    @Autowired
    private VisitRepository visitRepository;
    
    @Autowired
    private TripRepository tripRepository;
    
    @Autowired
    private ProcessedVisitRepository processedVisitRepository;
    
    public List<Integer> getAvailableYears() {
        // TODO: Replace with actual database query to get years with data
        // For now, return a range of recent years - this should be replaced with actual data query
        List<Integer> years = new ArrayList<>();
        int currentYear = java.time.LocalDate.now().getYear();
        for (int year = currentYear; year >= currentYear - 5; year--) {
            years.add(year);
        }
        return years;
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
        
        public TransportStatistic(String transportMode, double totalDistanceKm, int tripCount) {
            this.transportMode = transportMode;
            this.totalDistanceKm = totalDistanceKm;
            this.tripCount = tripCount;
        }
        
        public String getTransportMode() { return transportMode; }
        public double getTotalDistanceKm() { return totalDistanceKm; }
        public int getTripCount() { return tripCount; }
    }
    
    public List<VisitStatistic> getTopVisitsByStayTime(User user, Instant startTime, Instant endTime, int limit) {
        List<Object[]> results = processedVisitRepository.findTopPlacesByStayTime(user, startTime, endTime);
        
        return results.stream()
                .limit(limit)
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
        // TODO: Implement actual database query
        List<TransportStatistic> mockData = new ArrayList<>();
        mockData.add(new TransportStatistic("driving", 12500.5, 180));
        mockData.add(new TransportStatistic("walking", 850.2, 320));
        mockData.add(new TransportStatistic("cycling", 420.8, 45));
        mockData.add(new TransportStatistic("public_transport", 380.1, 28));
        return mockData.stream()
                .sorted((a, b) -> Double.compare(b.getTotalDistanceKm(), a.getTotalDistanceKm()))
                .collect(Collectors.toList());
    }
    
    public List<VisitStatistic> getOverallTopVisits(User user) {
        return getTopVisitsByStayTime(user, null, null, 5);
    }
    
    public List<TransportStatistic> getOverallTransportStatistics(User user) {
        return getTransportStatistics(user, null, null);
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
