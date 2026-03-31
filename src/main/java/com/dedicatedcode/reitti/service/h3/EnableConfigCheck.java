package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.config.H3MappingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EnableConfigCheck
{
    private static final Logger logger = LoggerFactory.getLogger(EnableConfigCheck.class);

    private final H3MappingConfig h3MappingConfig;
    private final List<AreaBoundaryLookupService> areaBoundaryLookupServices;
    private final AreaReverseLookupService areaReverseLookupService;

    public EnableConfigCheck(H3MappingConfig h3MappingConfig,
                             List<AreaBoundaryLookupService> areaBoundaryLookupServices,
                             Optional<AreaReverseLookupService> areaReverseLookupService)
    {
        this.h3MappingConfig = h3MappingConfig;
        this.areaBoundaryLookupServices = areaBoundaryLookupServices;
        this.areaReverseLookupService = areaReverseLookupService.orElse(null);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfig()
    {
        if (h3MappingConfig.isEnableAreaMapping() && !h3MappingConfig.isEnableH3Mapping())
        {
            logger.warn("Area mapping is enabled but h3 mapping is disabled. H3 indices will not be updated");
        }
        if (h3MappingConfig.isEnableAreaMapping())
        {
            if (areaBoundaryLookupServices.stream().filter(f -> !(f instanceof AreaBoundaryLookupServiceManager)).findAny().isEmpty())
            {
                throw new IllegalStateException(
                    "No boundary lookup service is available but area mapping is enabled. Maybe you need to configure"
                        + " nominatim?");
            }
            if (areaReverseLookupService == null)
            {
                throw new IllegalStateException(
                    "No reverse lookup service is available but area mapping is enabled. Maybe you need to configure "
                        + "photon?");
            }
        }
    }
}
