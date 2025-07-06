package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.importer.GeoJsonImporter;
import com.dedicatedcode.reitti.service.importer.GoogleTakeoutImporter;
import com.dedicatedcode.reitti.service.importer.GpxImporter;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

@Service
public class ImportHandler {
    
    private final GoogleTakeoutImporter googleTakeoutImporter;
    private final GpxImporter gpxImporter;
    private final GeoJsonImporter geoJsonImporter;

    public ImportHandler(
            GoogleTakeoutImporter googleTakeoutImporter,
            GpxImporter gpxImporter,
            GeoJsonImporter geoJsonImporter) {
        this.googleTakeoutImporter = googleTakeoutImporter;
        this.gpxImporter = gpxImporter;
        this.geoJsonImporter = geoJsonImporter;
    }
    
    public Map<String, Object> importGoogleTakeout(InputStream inputStream, User user) {
        return googleTakeoutImporter.importGoogleTakeout(inputStream, user);
    }

    public Map<String, Object> importGpx(InputStream inputStream, User user) {
        return gpxImporter.importGpx(inputStream, user);
    }

    public Map<String, Object> importGeoJson(InputStream inputStream, User user) {
        return geoJsonImporter.importGeoJson(inputStream, user);
    }
}
