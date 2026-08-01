package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.workbench.MovedPointDto;
import com.dedicatedcode.reitti.model.CoverageInformation;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    public void postPromotion(List<Long> insertedIds) {}

    @Override
    public void postDeletion(List<Long> deletedPointIds) {}

    @Override
    public void preMove(List<MovedPointDto> movedPoints) {}

    @Override
    public void postSourceRecalculation(List<Long> sourcePointIds) {}

    @Override
    public Optional<CoverageInformation> getCoverageInformation(User user, long osmId, Locale locale) {
        return Optional.empty();
    }

    @Override
    public Optional<CoverageInformation> getCoverageInformation(User user, Device device, long osmId, Locale locale) {
        return Optional.empty();
    }

    @Override
    public List<CoverageInformation> getCoverage(User user, Instant until, Locale locale) {
        return List.of();
    }

    @Override
    public List<CoverageInformation> getDeviceCoverage(User user, Device device, Instant until, Locale locale) {
        return List.of();
    }
}