package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.Page;
import com.dedicatedcode.reitti.model.PageRequest;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import org.springframework.stereotype.Service;

@Service
public class PlaceService {

    private final SignificantPlaceJdbcService significantPlaceJdbcService;

    public PlaceService(SignificantPlaceJdbcService significantPlaceJdbcService) {
        this.significantPlaceJdbcService = significantPlaceJdbcService;
    }

    public Page<SignificantPlace> getPlacesForUser(User user, PageRequest pageable) {
        return significantPlaceJdbcService.findByUser(user, pageable);
    }

}

