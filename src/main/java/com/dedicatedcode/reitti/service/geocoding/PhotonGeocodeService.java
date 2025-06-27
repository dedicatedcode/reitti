package com.dedicatedcode.reitti.service.geocoding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnExpression(
        "T(org.springframework.util.StringUtils).hasText('${reitti.geocoding.photon.base-url:}')"
)
public class PhotonGeocodeService implements GeocodeService {
    private final String baseUrl;

    public PhotonGeocodeService(@Value("${reitti.geocoding.photon.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
    @Override
    public String getName() {
        return "Photon";
    }

    @Override
    public String getUrlTemplate() {
        return baseUrl + "/reverse?lon={lng}&lat={lat}&limit=10&layer=house&layer=locality&radius=0.03";
    }
}
