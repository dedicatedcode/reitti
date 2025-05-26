package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ProcessedVisitRepository extends JpaRepository<ProcessedVisit, Long> {
    
    List<ProcessedVisit> findByUser(User user);
    
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
