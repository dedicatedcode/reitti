package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Long> {
    
    List<Visit> findByUser(User user);
    
    List<Visit> findByUserAndProcessedFalse(User user);

    List<Visit> findByUserAndStartTimeBetweenAndProcessedFalseOrderByStartTimeAsc(User user, Instant startTime, Instant endTime);

    Optional<Visit> findByUserAndStartTime(User user, Instant startTime);

    Optional<Visit> findByUserAndEndTime(User user, Instant departureTime);

    List<Visit> findByUserAndStartTimeBetweenOrderByStartTimeAsc(User user, Instant startTime, Instant endTime);

    List<Visit> findByUserAndStartTimeBeforeAndEndTimeAfter(User user, Instant startTimeBefore, Instant endTimeAfter);
}
