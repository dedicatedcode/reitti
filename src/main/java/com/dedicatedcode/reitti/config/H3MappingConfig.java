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

    public boolean isEnableH3Mapping()
    {
        return enableH3Mapping;
    }
}
