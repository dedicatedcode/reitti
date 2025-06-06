package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.GeocodeService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeocodeServiceRepository extends JpaRepository<GeocodeService, Long> {
    List<GeocodeService> findByEnabledTrueOrderByLastUsedAsc();
    List<GeocodeService> findAllByOrderByNameAsc();
}
