package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatisticsService {


    private final TripJdbcService tripJdbcService;

    private final ProcessedVisitJdbcService processedVisitJdbcService;

    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    public StatisticsService(TripJdbcService tripJdbcService,
                             ProcessedVisitJdbcService processedVisitJdbcService,
                             RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

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
        return rawLocationPointJdbcService.findDistinctYearsByUser(user);
    }

    public static class VisitStatistic {
        private final String placeName;
        private final double totalStayTimeHours;
        private final int visitCount;
        private final Double latitude;
        private final Double longitude;

        public VisitStatistic(String placeName, double totalStayTimeHours, int visitCount, Double latitude, Double longitude) {
            this.placeName = placeName;
            this.totalStayTimeHours = totalStayTimeHours;
            this.visitCount = visitCount;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getPlaceName() {
            return placeName;
        }

        public double getTotalStayTimeHours() {
            return totalStayTimeHours;
        }

        public int getVisitCount() {
            return visitCount;
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }
    }

    public static class TransportStatistic {
        private final String transportMode;
        private final double totalDistanceKm;
        private final int tripCount;
        private final double totalDurationHours;

        public TransportStatistic(String transportMode, double totalDistanceKm, double totalDurationHours, int tripCount) {
            this.transportMode = transportMode;
            this.totalDistanceKm = totalDistanceKm;
            this.totalDurationHours = totalDurationHours;
            this.tripCount = tripCount;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public double getTotalDistanceKm() {
            return totalDistanceKm;
        }

        public int getTripCount() {
            return tripCount;
        }

        public double getTotalDurationHours() {
            return totalDurationHours;
        }
    }

    public List<VisitStatistic> getTopVisitsByStayTime(User user, Instant startTime, Instant endTime, int limit) {
        List<Object[]> results;
        if (startTime == null || endTime == null) {
            results = processedVisitJdbcService.findTopPlacesByStayTimeWithLimit(user, limit);
        } else {
            results = processedVisitJdbcService.findTopPlacesByStayTimeWithLimit(user, startTime, endTime, limit);
        }

        return results.stream()
                .map(row -> {
                    String placeName = (String) row[0];
                    Long totalDurationSeconds = (Long) row[1];
                    Long visitCount = (Long) row[2];
                    Double latitude = (Double) row[3];
                    Double longitude = (Double) row[4];

                    // Convert seconds to hours with decimal precision
                    double totalStayTimeHours = totalDurationSeconds / 3600.0;
                    return new VisitStatistic(
                            placeName != null ? placeName : "Unknown Place",
                            totalStayTimeHours,
                            visitCount.intValue(),
                            latitude,
                            longitude
                    );
                })
                .collect(Collectors.toList());
    }

    public List<TransportStatistic> getTransportStatistics(User user, Instant startTime, Instant endTime) {
        List<Object[]> results = tripJdbcService.findTransportStatisticsByUserAndTimeRange(user, startTime, endTime);

        return results.stream()
                .map(StatisticsService::mapTransportStatistics)
                .collect(Collectors.toList());
    }

    public List<TransportStatistic> getTransportStatistics(User user) {
        List<Object[]> results = tripJdbcService.findTransportStatisticsByUser(user);

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

    public List<MonthlyTransportData> getMonthlyTransportBreakdown(User user, int year) {
        List<MonthlyTransportData> monthlyData = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            Instant startOfMonth = LocalDate.of(year, month, 1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endOfMonth = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();

            List<TransportStatistic> monthStats = getTransportStatistics(user, startOfMonth, endOfMonth);
            monthlyData.add(new MonthlyTransportData(java.time.Month.of(month).name(), monthStats));
        }

        return monthlyData;
    }

    public List<DailyTransportData> getDailyTransportBreakdown(User user, int year, int month) {
        List<DailyTransportData> dailyData = new ArrayList<>();
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Instant startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endOfDay = date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();

            List<TransportStatistic> dayStats = getTransportStatistics(user, startOfDay, endOfDay);
            dailyData.add(new DailyTransportData(date.getDayOfMonth(), dayStats));
        }

        return dailyData;
    }

    public static class MonthlyTransportData {
        private final String monthName;
        private final List<TransportStatistic> transportStats;

        public MonthlyTransportData(String monthName, List<TransportStatistic> transportStats) {
            this.monthName = monthName;
            this.transportStats = transportStats;
        }

        public String getMonthName() {
            return monthName;
        }

        public List<TransportStatistic> getTransportStats() {
            return transportStats;
        }
    }

    public static class DailyTransportData {
        private final int dayOfMonth;
        private final List<TransportStatistic> transportStats;

        public DailyTransportData(int dayOfMonth, List<TransportStatistic> transportStats) {
            this.dayOfMonth = dayOfMonth;
            this.transportStats = transportStats;
        }

        public int getDayOfMonth() {
            return dayOfMonth;
        }

        public List<TransportStatistic> getTransportStats() {
            return transportStats;
        }
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
