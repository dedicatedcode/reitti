package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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


    @Query("SELECT pv FROM ProcessedVisit pv WHERE pv.user = :user AND " +
            "pv.startTime <= :endTime AND pv.endTime >= :startTime")
    List<ProcessedVisit> findByUserAndTimeOverlap(User user, Instant startTime, Instant endTime);
    List<ProcessedVisit> findByUserAndStartTimeBetweenOrderByStartTimeAsc(
            User user, Instant startTime, Instant endTime);

    List<ProcessedVisit> findByUserAndEndTimeBetweenOrderByStartTimeAsc(User user, Instant endTimeAfter, Instant endTimeBefore);

    Optional<ProcessedVisit> findByUserAndId(User user, long id);

    List<ProcessedVisit> findByUserAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(User user, Instant startTimeIsGreaterThan, Instant endTimeIsLessThan);
}
