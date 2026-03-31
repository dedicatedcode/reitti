package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.config.H3MappingConfig;
import com.dedicatedcode.reitti.repository.h3.H3JdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class H3MappingService
{
    private static final Logger logger = LoggerFactory.getLogger(H3MappingService.class);

    private final H3JdbcService h3JdbcService;
    private final H3MappingConfig config;
    private boolean h3InitialMappingFinished = false;

    public H3MappingService(H3JdbcService h3JdbcService, H3MappingConfig config)
    {
        this.h3JdbcService = h3JdbcService;
        this.config = config;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyConsistency()
    {
        if (!h3JdbcService.checkH3MappingConsistency())
        {
            //TODO: this will make it impossible to start the application with inconsistent data
            //      thus fixing it from the application will not work
            throw new IllegalStateException("H3 boundary mapping consistency check failed");
        }
        logger.info("H3 mappings are consistent, all having the same resolution");
    }

    @EventListener(ApplicationReadyEvent.class)
    //TODO: do we want this to delay application start or not?
    @Async
    public void onStartupFinishH3Mappings() throws InterruptedException
    {
        var missingMappings = h3JdbcService.getNotH3MappedRawLocationPointsCount();
        if (missingMappings == 0)
        {
            logger.info("All points are mapped to their h3 index");
            h3InitialMappingFinished = true;
            return;
        }
        var expectedTimeS = missingMappings / config.getH3MappingBatchSize() * config.getH3MappingIntervalMs() / 1000;
        logger.info("Starting to map missing h3 indices, missing: {}. Estimated time: {}s", missingMappings,
            expectedTimeS);

        while (h3JdbcService.updateH3Mappings(config.getH3MappingBatchSize()) >= config.getH3MappingBatchSize())
        {
            Thread.sleep(config.getH3MappingIntervalMs());
        }
        logger.info("All h3 indices are mapped");
        h3InitialMappingFinished = true;
    }

    @Scheduled(fixedRateString = "${reitti.h3.mapping-interval-ms:10000}", initialDelay = 10000)
    public void doh3Mappings()
    {
        if (!h3InitialMappingFinished)
        {return;}
        var missing = h3JdbcService.getNotH3MappedRawLocationPointsCount();
        if (missing == 0) {
            return;
        }
        var mapped = h3JdbcService.updateH3Mappings(config.getH3MappingBatchSize());
        if (mapped < missing)
        {
            logger.warn(
                "Not all h3 indices were mapped, {}/{}. If this happens often the batchsize/interval might need "
                    + "adjustments. This is also expected after a huge import.", mapped, missing);
        } else
        {
            logger.trace("All missing h3 indices were mapped");
        }
    }
}
