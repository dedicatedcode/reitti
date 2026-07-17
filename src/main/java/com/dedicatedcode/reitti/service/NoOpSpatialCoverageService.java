package com.dedicatedcode.reitti.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", matchIfMissing = true, havingValue = "false")
public class NoOpSpatialCoverageService implements SpatialCoverageService {

}