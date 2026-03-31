package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.dto.area.AreaBounds;
import com.dedicatedcode.reitti.dto.area.AreaDescription;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@Primary
@ConditionalOnProperty(name = "reitti.h3.area-mapping.enabled", havingValue = "true")
public class AreaBoundaryLookupServiceManager implements AreaBoundaryLookupService
{

    private final List<AreaBoundaryLookupService> providers;

    public AreaBoundaryLookupServiceManager(List<AreaBoundaryLookupService> providers)
    {
        this.providers = providers;
    }

    @Override
    public Optional<String> getAreaBoundaryGeoJson(AreaDescription areaDescription, @Nullable AreaBounds areaBounds)
        throws IOException, InterruptedException
    {
        for (var provider : providers)
        {
            var result = provider.getAreaBoundaryGeoJson(areaDescription, areaBounds);
            if (result.isPresent())
            {
                return result;
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getAreaBoundaryGeoJson(AreaDescription areaDescription,
                                                   List<AreaDescription> parentAreaDescriptions)
        throws IOException, InterruptedException
    {
        for (var provider : providers)
        {
            var result = provider.getAreaBoundaryGeoJson(areaDescription, parentAreaDescriptions);
            if (result.isPresent())
            {
                return result;
            }
        }
        return Optional.empty();
    }

    @Override
    public int getOrder()
    {
        return -1;
    }
}
