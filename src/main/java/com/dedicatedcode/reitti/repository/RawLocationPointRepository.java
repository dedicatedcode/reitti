package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RawLocationPointRepository extends JpaRepository<RawLocationPoint, Long> {

    @Query("SELECT p FROM RawLocationPoint p WHERE p.userId = :userId AND p.timestamp BETWEEN :start AND :end")
    List<RawLocationPoint> findByUserIdAndTimestampBetween(@Param("userId") Long userId,
                                                           @Param("start") LocalDateTime start,
                                                           @Param("end") LocalDateTime end);

    @Query("SELECT p FROM RawLocationPoint p WHERE p.userId = :userId AND p.timestamp BETWEEN :start AND :end AND p.deviceId = :deviceId")
    List<RawLocationPoint> findByUserIdAndTimestampBetweenAndDevice(@Param("userId") Long userId,
                                                                    @Param("start") LocalDateTime start,
                                                                    @Param("end") LocalDateTime end,
                                                                    @Param("deviceId") Long deviceId);
}
