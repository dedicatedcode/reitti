package com.dedicatedcode.reitti.controller.api.ingestion.owntracks;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.dto.ReittiRemoteInfo;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.integration.ReittiIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import com.dedicatedcode.reitti.service.RequestFailedException;
import com.dedicatedcode.reitti.service.RequestTemporaryFailedException;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ingest")
public class OwntracksIngestionApiController {
    private static final Logger logger = LoggerFactory.getLogger(OwntracksIngestionApiController.class);
    private final UserJdbcService userJdbcService;
    private final LocationBatchingService locationBatchingService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final AvatarService avatarService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final ReittiIntegrationService reittiIntegrationService;

    public OwntracksIngestionApiController(UserJdbcService userJdbcService,
                                           LocationBatchingService locationBatchingService,
                                           RawLocationPointJdbcService rawLocationPointJdbcService,
                                           AvatarService avatarService,
                                           UserSharingJdbcService userSharingJdbcService, ReittiIntegrationService reittiIntegrationService) {
        this.userJdbcService = userJdbcService;
        this.locationBatchingService = locationBatchingService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.avatarService = avatarService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.reittiIntegrationService = reittiIntegrationService;
    }

    @PostMapping("/owntracks")
    public ResponseEntity<?> receiveOwntracksData(@RequestBody OwntracksLocationRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = this.userJdbcService.findByUsername(userDetails.getUsername()).orElseThrow(() -> new UsernameNotFoundException(userDetails.getUsername()));

        try {
            if (!request.isLocationUpdate()) {
                logger.debug("Ignoring non-location Owntracks message of type: {}", request.getType());
                // Return empty array for non-location messages
                return ResponseEntity.ok(new ArrayList<OwntracksFriendResponse>());
            }

            // Convert an Owntracks format to our LocationPoint format
            LocationPoint locationPoint = request.toLocationPoint();

            if (locationPoint.getTimestamp() == null) {
                logger.warn("Ignoring location point [{}] because timestamp is null", locationPoint);
                // Return empty array when timestamp is null
                return ResponseEntity.ok(new ArrayList<OwntracksFriendResponse>());
            }

            this.locationBatchingService.addLocationPoint(user, locationPoint);
            logger.debug("Successfully received and queued Owntracks location point for user {}",
                         user.getUsername());

            // Return friends data
            List<OwntracksFriendResponse> friendsData = buildFriendsData(user);
            return ResponseEntity.ok(friendsData);

        } catch (Exception e) {
            logger.error("Error processing Owntracks data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing Owntracks data: " + e.getMessage()));
        }
    }

    private List<OwntracksFriendResponse> buildFriendsData(User user) {
        List<OwntracksFriendResponse> friendsData = new ArrayList<>();

        // Local shared users
        List<UserSharing> sharedUsers = userSharingJdbcService.findBySharedWithUser(user.getId());
        for (UserSharing sharing : sharedUsers) {
            Optional<User> sharedUserOpt = userJdbcService.findById(sharing.getSharingUserId());
            sharedUserOpt.ifPresent(sharedUser -> {
                String tid = generateTid(sharedUser.getUsername());

                // Get processed avatar thumbnail (192x192)
                Optional<byte[]> avatarThumbnail = avatarService.getAvatarThumbnail(sharedUser.getId(), 192, 192);

                // Add card with processed avatar
                friendsData.add(new OwntracksFriendResponse(
                        tid,
                        sharedUser.getDisplayName(),
                        avatarThumbnail.orElse(null),
                        "image/jpeg" // Default to JPEG for OwnTracks
                ));

                // Add location if available
                Optional<RawLocationPoint> latestLocation = rawLocationPointJdbcService.findLatest(sharedUser);
                latestLocation.ifPresent(location -> {
                    friendsData.add(new OwntracksFriendResponse(
                            tid,
                            sharedUser.getDisplayName(),
                            location.getLatitude(),
                            location.getLongitude(),
                            location.getTimestamp().getEpochSecond()
                    ));
                });
            });
        }

        // Remote integrations
        List<ReittiIntegration> integrations = reittiIntegrationService.getActiveIntegrationsForUser(user);
        for (ReittiIntegration integration : integrations) {
            try {
                ReittiRemoteInfo info = reittiIntegrationService.getInfo(integration);
                String tid = generateTid(info.userInfo().username());
                friendsData.add(new OwntracksFriendResponse(tid, info.userInfo().displayName(), null, null));
            } catch (RequestFailedException | RequestTemporaryFailedException e) {
                logger.warn("Couldn't fetch info for integration {}", integration.getId(), e);
            }
        }

        return friendsData;
    }

    private String generateTid(String username) {
        return username != null && username.length() >= 2 ?
                username.substring(0, 2).toUpperCase() : "UN";
    }
}
