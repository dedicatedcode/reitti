package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.area.AreaDescription;
import com.dedicatedcode.reitti.dto.area.AreaMappingRequest;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.repository.h3.AreaJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;

@Service
@ConditionalOnBean(AreaReverseLookupService.class)
@ConditionalOnProperty(name = "reitti.h3.area-mapping.enabled", havingValue = "true")
public class AreaIngestListener
{
    private static final Logger logger = LoggerFactory.getLogger(AreaIngestListener.class);

    private final AreaBoundaryLookupService areaBoundaryLookupService;
    private final AreaReverseLookupService areaReverseLookupService;
    private final AreaJdbcService areaJdbcService;
    private final RabbitTemplate rabbitTemplate;

    public AreaIngestListener(AreaBoundaryLookupService areaBoundaryLookupService,
                              AreaReverseLookupService areaReverseLookupService,
                              AreaJdbcService areaJdbcService, RabbitTemplate rabbitTemplate)
    {
        this.areaBoundaryLookupService = areaBoundaryLookupService;
        this.areaReverseLookupService = areaReverseLookupService;
        this.areaJdbcService = areaJdbcService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.AREA_INGESTION_QUEUE, concurrency = "1-1")
    public void handleBoundaryMappingRequest(AreaMappingRequest request)
    {
        var boundaries = request.areaDescriptions();
        // Ensure areas are in correct order: larger to smaller
        boundaries.sort(Comparator.comparing(AreaDescription::type));
        Optional<Long> lastId = Optional.empty();
        for (var boundary : boundaries)
        {
            var existingBoundaryId = areaJdbcService.getAreaId(boundary, lastId.orElse(null));
            if (existingBoundaryId.isPresent())
            {
                areaJdbcService.connectAreaWithPlace(existingBoundaryId.get(), request.significantPlaceId());
                lastId = existingBoundaryId;
            } else
            {
                lastId = Optional.of(areaJdbcService.storeUnmappedArea(boundary, lastId.orElse(null)));
            }
            lastId.ifPresent(aLong -> rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.AREA_BOUNDARY_INGESTION_QUEUE, aLong));
        }
    }

    @RabbitListener(queues = RabbitMQConfig.AREA_BOUNDARY_INGESTION_QUEUE, concurrency = "1-1")
    public void handleUnmappedArea(long areaId) throws IOException, InterruptedException {
        //TODO: check areas parents are mapped, else requeue request
        var geoFence = areaJdbcService.getAreaBestGeoFence(areaId);
        var maybeArea = areaJdbcService.getArea(areaId);
        if (maybeArea.isEmpty()) {
            logger.warn("Couldn't load area with id {} from database, skipping", areaId);
            return;
        }
        var area = maybeArea.get();

        var boundaryGeoJson = areaBoundaryLookupService.getAreaBoundaryGeoJson(area, geoFence.orElse(null));
        if (boundaryGeoJson.isEmpty())
        {
            logger.warn("Couldn't get boundary for {}: {}", area.type(), area.name());
            areaJdbcService.markAreaAsMapped(areaId, null);
        } else
        {
            areaJdbcService.markAreaAsMapped(areaId, boundaryGeoJson.get());
            areaJdbcService.updateAreaMapping(areaId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void addMissingBoundaryMappings() throws IOException, InterruptedException
    {
        var missing = areaJdbcService.findSignificantPlacesWithoutAreaMapping();
        for (var place : missing)
        {
            scheduleMapping(place.getId(), place.getLatitudeCentroid(), place.getLongitudeCentroid());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void queueMissingAreaBoundaries()
    {
        var missing = areaJdbcService.getUnmappedAreaIds();
        logger.info("{} areas are missing boundaries, scheduling retrieval.", missing.size());
        for (var id : missing)
        {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.AREA_BOUNDARY_INGESTION_QUEUE,
                id);
        }
    }

    public void scheduleMapping(long significantPlaceId, double latitude, double longitude)
        throws IOException, InterruptedException
    {
        var boundaries = areaReverseLookupService.getAreaHierarchy(new GeoPoint(latitude, longitude));

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.AREA_INGESTION_QUEUE,
            new AreaMappingRequest(significantPlaceId, boundaries));
    }
}
