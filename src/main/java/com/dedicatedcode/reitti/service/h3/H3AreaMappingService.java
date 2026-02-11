package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.config.H3MappingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "reitti.h3.area-mapping.enabled", havingValue = "true")
public class H3AreaMappingService
{
    private static final Logger logger = LoggerFactory.getLogger(H3AreaMappingService.class);

    private boolean initialMappingFinished;
    private final AreaJdbcService areaJdbcService;
    private final H3MappingConfig config;

    public H3AreaMappingService(AreaJdbcService areaJdbcService, H3MappingConfig config)
    {
        this.areaJdbcService = areaJdbcService;
        this.config = config;
        this.initialMappingFinished = false;
    }

    @EventListener(ApplicationReadyEvent.class)
    //TODO: do we want this to delay application start or not?
    //      will it be a problem if this is not finished when application starts?
    @Async
    public void onStartupH3AreaMapping() throws InterruptedException
    {
        var missingMappings = areaJdbcService.getUnmappedH3IndexCount();
        if (missingMappings == 0)
        {
            logger.info("All h3 hexagons are mapped to their corresponding areas");
            initialMappingFinished = true;
            return;
        }
        var expectedTimeS = missingMappings / config.getAreaMappingBatchSize() * config.getAreaMappingDelayMs() / 1000;
        logger.info("Starting to map h3 indices to their areas, missing: {}. Estimated time: {}s", missingMappings,
            expectedTimeS);

        while (areaJdbcService.updatePointsAreaBatch(config.getAreaMappingBatchSize())
            >= config.getAreaMappingBatchSize())
        {
            Thread.sleep(config.getAreaMappingDelayMs());
        }
        logger.info("All h3 indices are mapped to their corresponding areas");
        initialMappingFinished = true;
    }

    @Scheduled(fixedRateString = "${reitti.h3.mapping-interval-ms:10000}", initialDelay = 10000)
    public void doh3Mappings()
    {
        if (!initialMappingFinished)
        {return;}
        var missing = areaJdbcService.getUnmappedH3IndexCount();
        if (missing == 0) {
            return;
        }
        var mapped = areaJdbcService.updatePointsAreaBatch(config.getAreaMappingBatchSize());
        if (mapped < missing)
        {
            logger.warn(
                "Not all h3 indices were mapped to their corresponding areas, {}/{}. If this happens often the batchsize/interval might need "
                    + "adjustments. This is also expected after a huge import.", mapped, missing);
        } else
        {
            logger.trace("All missing h3 indices were mapped to their areas");
        }
    }
}
