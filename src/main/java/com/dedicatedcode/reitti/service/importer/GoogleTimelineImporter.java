package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.User;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

@Component
public class GoogleTimelineImporter {
    
    private final GoogleAndroidTimelineImporter androidImporter;
    private final GoogleIOSTimelineImporter iosImporter;

    public GoogleTimelineImporter(GoogleAndroidTimelineImporter androidImporter,
                                  GoogleIOSTimelineImporter iosImporter) {
        this.androidImporter = androidImporter;
        this.iosImporter = iosImporter;
    }
    
    public Map<String, Object> importGoogleTimelineFromIOS(InputStream inputStream, User user) {
        return iosImporter.importGoogleTimelineFromIOS(inputStream, user);
    }

    public Map<String, Object> importGoogleTimelineFromAndroid(InputStream inputStream, User user) {
        return androidImporter.importGoogleTimelineFromAndroid(inputStream, user);
    }
}
