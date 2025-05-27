package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignificantPlaceRepository extends JpaRepository<SignificantPlace, Long> {
    
    List<SignificantPlace> findByUser(User user);
    
    Page<SignificantPlace> findByUser(User user, Pageable pageable);
    
    @Query(value = "SELECT sp.* FROM significant_places sp " +
            "WHERE sp.user_id = :userId " +
            "AND ST_DWithin(sp.geom, :point, :distance)", 
            nativeQuery = true)
    List<SignificantPlace> findNearbyPlaces(
            @Param("userId") Long userId,
            @Param("point") Point point,
            @Param("distance") double distanceInMeters);
}
