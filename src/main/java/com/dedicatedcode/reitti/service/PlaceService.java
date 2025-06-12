package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.SignificantPlaceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PlaceService {

    private final SignificantPlaceRepository placeRepository;

    public PlaceService(SignificantPlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public Page<SignificantPlace> getPlacesForUser(User user, Pageable pageable) {
        return placeRepository.findByUser(user, pageable);
    }

    @Transactional
    public void updatePlaceName(Long placeId, String name, User currentUser) {
        Optional<SignificantPlace> placeOpt = placeRepository.findById(placeId);
        
        if (placeOpt.isEmpty()) {
            throw new IllegalArgumentException("Place not found with ID: " + placeId);
        }
        
        SignificantPlace place = placeOpt.get();
        
        if (!place.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You don't have permission to update this place");
        }
        
        place.setName(name);
        placeRepository.save(place);
    }
}

