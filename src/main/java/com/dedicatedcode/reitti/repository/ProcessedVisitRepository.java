package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedVisitRepository extends JpaRepository<ProcessedVisit, Long> {

    @Query("SELECT pv FROM ProcessedVisit pv WHERE pv.user = :user AND " +
            "pv.startTime <= :endTime AND pv.endTime >= :startTime")
    List<ProcessedVisit> findByUserAndTimeOverlap(User user, Instant startTime, Instant endTime);

    List<ProcessedVisit> findByUserAndStartTimeBetweenOrderByStartTimeAsc(
            User user, Instant startTime, Instant endTime);

    List<ProcessedVisit> findByUserAndEndTimeBetweenOrderByStartTimeAsc(User user, Instant endTimeAfter, Instant endTimeBefore);

    Optional<ProcessedVisit> findByUserAndId(User user, long id);

    List<ProcessedVisit> findByUserAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(User user, Instant startTimeIsGreaterThan, Instant endTimeIsLessThan);

    @Query("SELECT pv.place.name, SUM(pv.durationSeconds), COUNT(pv), pv.place.latitudeCentroid, pv.place.longitudeCentroid " +
            "FROM ProcessedVisit pv " +
            "WHERE pv.user = :user " +
            "GROUP BY pv.place.id, pv.place.name, pv.place.latitudeCentroid, pv.place.longitudeCentroid " +
            "ORDER BY SUM(pv.durationSeconds) DESC LIMIT :limit")
    List<Object[]> findTopPlacesByStayTimeWithLimit(@Param("user") User user,
                                                    @Param("limit") long limit);

    @Query("SELECT pv.place.name, SUM(pv.durationSeconds), COUNT(pv), pv.place.latitudeCentroid, pv.place.longitudeCentroid " +
            "FROM ProcessedVisit pv " +
            "WHERE pv.user = :user " +
            "AND pv.startTime >= :startTime " +
            "AND pv.endTime <= :endTime " +
            "GROUP BY pv.place.id, pv.place.name, pv.place.latitudeCentroid, pv.place.longitudeCentroid " +
            "ORDER BY SUM(pv.durationSeconds) DESC LIMIT :limit")
    List<Object[]> findTopPlacesByStayTimeWithLimit(@Param("user") User user,
                                                    @Param("startTime") Instant startTime,
                                                    @Param("endTime") Instant endTime,
                                                    @Param("limit") long limit);
}
