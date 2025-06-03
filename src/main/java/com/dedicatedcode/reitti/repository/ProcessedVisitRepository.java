package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ProcessedVisitRepository extends JpaRepository<ProcessedVisit, Long> {
    
    List<ProcessedVisit> findByUser(User user);

    List<ProcessedVisit> findByUserOrderByStartTime(User user);

    @Query("SELECT pv FROM ProcessedVisit pv WHERE pv.user = :user AND pv.place = :place " +
           "AND ((pv.startTime <= :endTime AND pv.endTime >= :startTime) OR " +
           "(pv.startTime >= :startTime AND pv.startTime <= :endTime) OR " +
           "(pv.endTime >= :startTime AND pv.endTime <= :endTime))")
    List<ProcessedVisit> findByUserAndPlaceAndTimeOverlap(
            @Param("user") User user,
            @Param("place") SignificantPlace place,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    @Query("SELECT pv FROM ProcessedVisit pv WHERE pv.user = :user " +
           "AND ((pv.startTime <= :endTime AND pv.endTime >= :startTime) OR " +
           "(pv.startTime >= :startTime AND pv.startTime <= :endTime) OR " +
           "(pv.endTime >= :startTime AND pv.endTime <= :endTime))")
    List<ProcessedVisit> findByUserAndTimeOverlap(
            @Param("user") User user,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    List<ProcessedVisit> findByUserAndStartTimeBetweenOrderByStartTimeAsc(
            User user, Instant startTime, Instant endTime);

    @Query("SELECT pv FROM ProcessedVisit pv WHERE pv.user = ?1 AND pv.place = ?2 AND " +
           "((pv.startTime <= ?3 AND pv.endTime >= ?3) OR " +
           "(pv.startTime <= ?4 AND pv.endTime >= ?4) OR " +
           "(pv.startTime >= ?3 AND pv.endTime <= ?4))")
    List<ProcessedVisit> findOverlappingVisits(User user, SignificantPlace place, 
                                              Instant startTime, Instant endTime);
    
    @Query("SELECT pv FROM ProcessedVisit pv WHERE pv.user = ?1 AND pv.place = ?2 AND " +
           "((pv.endTime >= ?3 AND pv.endTime <= ?4) OR " +
           "(pv.startTime >= ?3 AND pv.startTime <= ?4))")
    List<ProcessedVisit> findVisitsWithinTimeRange(User user, SignificantPlace place, 
                                                  Instant startThreshold, Instant endThreshold);
}
