package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.AbstractIntegrationTest;
import com.dedicatedcode.reitti.MockImportListener;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationDataServiceTest extends AbstractIntegrationTest {

    @Autowired
    private LocationDataService locationDataService;

    @Autowired
    private ImportHandler importHandler;

    @Autowired
    private MockImportListener importListener;

    @Autowired
    private RawLocationPointRepository rawLocationPointRepository;

    @Test
    void shouldStoreAllRawLocationPoints() {
        InputStream is = getClass().getResourceAsStream("/data/gpx/20250531.gpx");
        importHandler.importGpx(is, user);

        List<LocationDataRequest.LocationPoint> allPoints = this.importListener.getPoints();

        locationDataService.processLocationData(user, allPoints);

        assertEquals(2567, rawLocationPointRepository.count());
    }

    @Test
    void shouldSkipDuplicatedRawLocationPoints() {
        InputStream is = getClass().getResourceAsStream("/data/gpx/20250531.gpx");
        importHandler.importGpx(is, user);

        List<LocationDataRequest.LocationPoint> allPoints = this.importListener.getPoints();

        locationDataService.processLocationData(user, allPoints);
        locationDataService.processLocationData(user, allPoints);

        assertEquals(2567, rawLocationPointRepository.count());
    }
}