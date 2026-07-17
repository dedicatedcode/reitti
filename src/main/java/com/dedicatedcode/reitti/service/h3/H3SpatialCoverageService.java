package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.service.SpatialCoverageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class H3SpatialCoverageService implements SpatialCoverageService {

    private final RocksDBH3Service rocksDbService;

    public H3SpatialCoverageService(RocksDBH3Service rocksDbService) {
        this.rocksDbService = rocksDbService;
    }
}
