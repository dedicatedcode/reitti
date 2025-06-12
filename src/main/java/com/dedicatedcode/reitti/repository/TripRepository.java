package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    
    List<Trip> findByUser(User user);
    
    List<Trip> findByUserAndStartTimeBetweenOrderByStartTimeAsc(
            User user, Instant startTime, Instant endTime);

    @Query("SELECT pv FROM Trip pv WHERE pv.user = :user " +
            "AND ((pv.startTime <= :endTime AND pv.endTime >= :startTime) OR " +
            "(pv.startTime >= :startTime AND pv.startTime <= :endTime) OR " +
            "(pv.endTime >= :startTime AND pv.endTime <= :endTime))")
    List<Trip> findByUserAndTimeOverlap(
            @Param("user") User user,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    boolean existsByUserAndStartTimeAndEndTime(User user, Instant startTime, Instant endTime);

    boolean existsByUserAndStartPlaceAndEndPlaceAndStartTimeAndEndTime(User user, SignificantPlace startPlace, SignificantPlace endPlace, Instant startTime, Instant endTime);

    List<Trip> findByUserAndStartVisitOrEndVisit(User user, ProcessedVisit startVisit, ProcessedVisit endVisit);
}
