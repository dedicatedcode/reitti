package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.dto.area.AreaBounds;
import com.dedicatedcode.reitti.dto.area.AreaDescription;
import com.dedicatedcode.reitti.repository.h3.AreaJdbcService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnExpression(
    "${reitti.h3.area-mapping.enabled:false} && '${reitti.geocoding.nominatim.use.official.api:}' == 'I have read "
        + "nominatims usage policy'")
public class NominatimRateLimitedAreaBoundaryLookupService extends NominatimAreaBoundaryLookupService
    implements DisposableBean
{

    private final Semaphore semaphore = new Semaphore(1);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public NominatimRateLimitedAreaBoundaryLookupService(AreaJdbcService areaJdbcService)
    {
        super("https://nominatim.openstreetmap.org/", areaJdbcService);
    }

    @Override
    public String getName()
    {
        return "Official Nominatim";
    }

    @Override
    public Optional<String> getAreaBoundaryGeoJson(AreaDescription areaDescription, @Nullable AreaBounds areaBounds)
        throws IOException, InterruptedException
    {
        // This one is rate limited to 1 request every 10 seconds. Api doc requires at least 1 second, but lets be
        // even more fair.
        semaphore.acquire();

        try
        {
            return super.getAreaBoundaryGeoJson(areaDescription, areaBounds);
        } finally
        {
            scheduler.schedule(() -> semaphore.release(), 10, TimeUnit.SECONDS);
        }
    }

    @Override
    public int getOrder()
    {
        // This one always has to be last
        return Integer.MAX_VALUE;
    }

    @Override
    public void destroy() throws Exception
    {
        scheduler.shutdown();
    }
}
