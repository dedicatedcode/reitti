package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.CoverageInformation;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", matchIfMissing = true, havingValue = "false")
public class NoOpSpatialCoverageService implements SpatialCoverageService {

    @Override
    public Long getLevelCellForPoint(double latitude, double longitude, int resolution) {
        return null;
    }

    @Override
    public void postPromotion(List<Long> insertedIds) { }

    @Override
    public Optional<CoverageInformation> getCoverageInformation(User user, Device device, long osmId, Locale locale) {
        return Optional.empty();
    }
}