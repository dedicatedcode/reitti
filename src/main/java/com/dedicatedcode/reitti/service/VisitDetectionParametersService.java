package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.PreviewVisitDetectionParametersJdbcService;
import com.dedicatedcode.reitti.repository.VisitDetectionParametersJdbcService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class VisitDetectionParametersService {
    private final VisitDetectionParametersJdbcService visitDetectionParametersJdbcService;
    private final PreviewVisitDetectionParametersJdbcService previewVisitDetectionParametersJdbcService;

    public VisitDetectionParametersService(VisitDetectionParametersJdbcService visitDetectionParametersJdbcService,
                                           PreviewVisitDetectionParametersJdbcService previewVisitDetectionParametersJdbcService) {
        this.visitDetectionParametersJdbcService = visitDetectionParametersJdbcService;
        this.previewVisitDetectionParametersJdbcService = previewVisitDetectionParametersJdbcService;
    }

    public DetectionParameter getCurrentConfiguration(User user, String previewId) {
        return this.previewVisitDetectionParametersJdbcService.findCurrent(user, previewId);
    }
    public DetectionParameter getCurrentConfiguration(User user, Instant instant) {
        return this.visitDetectionParametersJdbcService.findCurrent(user, instant);

    }
}
