package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.service.SpatialCoverageService;
import com.uber.h3core.H3Core;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class H3SpatialCoverageService implements SpatialCoverageService {

    private final RocksDBH3Service rocksDbService;
    private final H3Core h3;

    public H3SpatialCoverageService(RocksDBH3Service rocksDbService) throws IOException {
        this.rocksDbService = rocksDbService;
        this.h3 = H3Core.newInstance();
    }

    @Override
    public Long getLevelCellForPoint(double latitude, double longitude, int resolution) {
        return h3.latLngToCell(latitude, longitude, resolution);
    }
}
