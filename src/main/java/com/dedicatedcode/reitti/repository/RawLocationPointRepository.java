package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RawLocationPointRepository extends JpaRepository<RawLocationPoint, Long> {
    List<RawLocationPoint> findByUserOrderByTimestampAsc(User user);
    
    List<RawLocationPoint> findByUserAndTimestampBetweenOrderByTimestampAsc(
            User user, Instant startTime, Instant endTime);
    
    @Query("SELECT r FROM RawLocationPoint r WHERE r.user = ?1 AND DATE(r.timestamp) = DATE(?2) ORDER BY r.timestamp ASC")
    List<RawLocationPoint> findByUserAndDate(User user, Instant date);
    
    Optional<RawLocationPoint> findByUserAndTimestamp(User user, Instant timestamp);
}
