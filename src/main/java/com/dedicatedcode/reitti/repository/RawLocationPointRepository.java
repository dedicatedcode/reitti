package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RawLocationPointRepository extends JpaRepository<RawLocationPoint, Long> {

    List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime);

    Optional<RawLocationPoint> findByUserAndTimestamp(User user, Instant timestamp);

    @Query("SELECT p as point, ST_ClusterDBSCAN(p.geom, :distance, :minPoints) over () AS clusterId " +
            "FROM RawLocationPoint p " +
            "WHERE p.user = :user " +
            "AND p.timestamp BETWEEN :start AND :end")
    List<ClusteredPoint> findClusteredPointsInTimeRangeForUser(
            @Param("user") User user,
            @Param("start") Instant startTime,
            @Param("end") Instant endTime,
            @Param("minPoints") int minimumPoints,
            @Param("distance") double distanceInMeters
    );

    List<RawLocationPoint> findByUserAndProcessedIsFalseOrderByTimestamp(User user);

    @Query("SELECT DISTINCT EXTRACT(YEAR FROM p.timestamp) " +
           "FROM RawLocationPoint p " +
           "WHERE p.user = :user " +
           "ORDER BY EXTRACT(YEAR FROM p.timestamp) DESC")
    List<Integer> findDistinctYearsByUser(@Param("user") User user);

    interface ClusteredPoint {
        RawLocationPoint getPoint();
        Integer getClusterId();
    }
}
