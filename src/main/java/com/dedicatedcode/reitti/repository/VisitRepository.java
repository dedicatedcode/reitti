package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Long> {
    
    List<Visit> findByUser(User user);
    
    List<Visit> findByUserAndProcessedFalse(User user);
    
    List<Visit> findByUserAndStartTimeBetweenOrderByStartTimeAsc(
            User user, Instant startTime, Instant endTime);

    List<Visit> findByUserAndStartTimeBetweenAndProcessedFalseOrderByStartTimeAsc(User user, Instant startTime, Instant endTime);
}
