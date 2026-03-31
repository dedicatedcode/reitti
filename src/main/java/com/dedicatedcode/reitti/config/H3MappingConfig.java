package com.dedicatedcode.reitti.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class H3MappingConfig
{
    /**
     * Whether to enable the h3 system all together.
     * <p>
     * Enabling the h3 system allows the collection of world coverage stats but introduces additional computation and
     * memory costs.
     * <p>
     * This system can be enabled at any time and will catch up with existing data.
     * <p>
     * Disabling the h3 system will <b>NOT</b> remove any data and simply stop further mappings.
     *
     */
    @Value("${reitti.h3.enabled:false}")
    private boolean enableH3Mapping;

    /**
     * Whether to enable the mapping part of the h3 system.
     * <p>
     * The area mapping is used to generate coverage statistics for areas: e.g., 1.45% of Berlin, 0.1% of Germany, ...
     * <p>
     * Same as the entire H3 system, this can be enabled at any time in the future and will catch up with existing
     * data.
     * <p>
     * Disabling the area mapping will <b>NOT</b> remove any data and simply stop further mappings.
     * <p>
     * After enabling the area mapping will take some time on startup. This time depends on visited locations and
     * available geocoding capabilities. Before enabling it is highly recommended to set up your own nominatim service.
     */
    @Value("${reitti.h3.area-mapping.enabled:false}")
    private boolean enableAreaMapping;

    /**
     * Resolution to use for H3 indices.
     * <p>
     * For more on size and area see: <a href="https://h3geo.org/docs/core-library/restable/">H3 documentation</a>
     * <p>
     * <b>Note</b>: If changed, all h3 indices should be regenerated, current logic cannot work with different
     * resolutions and will yield incorrect results.
     */
    @Value("${reitti.h3.resolution:13}")
    private int targetResolution;

    /**
     * How many neighbors should be revealed:
     * <ul>
     *     <li>0: only actual hex</li>
     *     <li>n: additional rings</li>
     * </list>
     */
    @Value("${reitti.h3.reveal-neighbours:2}")
    private int h3RevealNeighbours;

    /**
     * How many raw location points to map to their h3 indices per batch.
     * <p>
     * Bigger values yield faster mapping but require more memory.
     * <p>
     * After initial ingestion, depending on {@link H3MappingConfig#h3MappingIntervalMs}, it's not expected to be
     * relevant anymore. If fewer points than batch size are ingested during {@link H3MappingConfig#h3MappingIntervalMs}
     * time, this value has no effect.
     * <p>
     * Thus big imports also use this value to split database operations and reduce database pressure. As long as
     * mapping is not slower than point ingestion, this does not matter for usability as it mainly affects the delay
     * between a point being ingested and it being mapped to its h3 index and thus being used in the statistics.
     */
    @Value("${reitti.h3.mapping-batch-size:1000}")
    private int h3MappingBatchSize;

    /**
     * How often to check for missing h3 mappings.
     * <p>
     * This is used both during initial ingestion to reduce database pressure and later during normal operation.
     * <p>
     * Higher values reduce database operations but increase delay between point ingestion and handling.
     */
    @Value("${reitti.h3.mapping-interval-ms:10000}")
    private int h3MappingIntervalMs;

    /**
     * How many h3 indices to map to their corresponding boundaries per batch.
     * <p>
     * Bigger values yield faster mapping but require more memory.
     * <p>
     * This is mostly used during initial ingestion, after that only small batch sizes are expected depending on
     * {@link H3MappingConfig#areaMappingDelayMs}.
     */
    @Value("${reitti.h3.area-mapping.batch-size:1000}")
    private int areaMappingBatchSize;

    /**
     * How long to wait between each batch of area mappings.
     * <p>
     * Area mappings are the process of checking which area an h3 index belongs to. This includes all types: country,
     * city, ...
     * <p>
     * This value is only used during initial ingestion on application startup and should reduce database pressure.
     * Afterward mapping is done on demand.
     */
    @Value("${reitti.h3.area-mapping.delay-ms:1000}")
    private int areaMappingDelayMs;

    public boolean isEnableH3Mapping()
    {
        return enableH3Mapping;
    }

    public boolean isEnableAreaMapping()
    {
        return enableAreaMapping;
    }

    public int getH3RevealNeighbours()
    {
        return h3RevealNeighbours;
    }

    public int getTargetResolution()
    {
        return targetResolution;
    }

    public int getH3MappingIntervalMs()
    {
        return h3MappingIntervalMs;
    }

    public int getH3MappingBatchSize()
    {
        return h3MappingBatchSize;
    }

    public int getAreaMappingDelayMs()
    {
        return areaMappingDelayMs;
    }

    public int getAreaMappingBatchSize()
    {
        return areaMappingBatchSize;
    }
}
