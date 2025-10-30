package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MemoryTripJdbcService;
import com.dedicatedcode.reitti.repository.MemoryVisitJdbcService;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.MemoryService;
import com.dedicatedcode.reitti.service.StorageService;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.dedicatedcode.reitti.model.Role.ADMIN;
import static com.dedicatedcode.reitti.model.Role.USER;

@Controller
@RequestMapping("/memories/{memoryId}/blocks")
public class MemoryBlockController {

    private final MemoryService memoryService;
    private final ImmichIntegrationService immichIntegrationService;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final MemoryVisitJdbcService memoryVisitJdbcService;
    private final MemoryTripJdbcService memoryTripJdbcService;
    private final StorageService storageService;

    public MemoryBlockController(MemoryService memoryService, ImmichIntegrationService immichIntegrationService, TripJdbcService tripJdbcService, ProcessedVisitJdbcService processedVisitJdbcService, MemoryVisitJdbcService memoryVisitJdbcService, MemoryTripJdbcService memoryTripJdbcService, StorageService storageService) {
        this.memoryService = memoryService;
        this.immichIntegrationService = immichIntegrationService;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.memoryVisitJdbcService = memoryVisitJdbcService;
        this.memoryTripJdbcService = memoryTripJdbcService;
        this.storageService = storageService;
    }

    @DeleteMapping("/{blockId}")
    public String deleteBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        memoryService.deleteBlock(blockId);
        
        if (hxRequest != null) {
            return "memories/fragments :: empty";
        }
        
        return "redirect:/memories/" + memoryId;
    }

    @GetMapping("/{blockId}/edit")
    public String editBlockForm(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        MemoryBlock block = memoryService.getBlockById(user, blockId)
                .orElseThrow(() -> new IllegalArgumentException("Block not found"));
        
        model.addAttribute("memoryId", memoryId);
        model.addAttribute("block", block);

        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));

        switch (block.getBlockType()) {
            case IMAGE_GALLERY:
                boolean immichEnabled = immichIntegrationService.getIntegrationForUser(user)
                        .map(ImmichIntegration::isEnabled)
                        .orElse(false);
                model.addAttribute("immichEnabled", immichEnabled);
                model.addAttribute("imageBlock", memoryService.getBlock(user, timezone, memoryId, blockId).orElseThrow(() -> new IllegalArgumentException("Block not found")) );
                return "memories/blocks/edit :: edit-image-gallery-block";
            case TEXT:
                memoryService.getBlock(user, timezone, memoryId, blockId).ifPresent(text ->
                    model.addAttribute("textBlock", text));
                return "memories/blocks/edit :: edit-text-block";
            case CLUSTER_VISIT:
                memoryService.getBlock(user, timezone, memoryId, blockId).ifPresent(b ->
                        model.addAttribute("clusterVisitBlock", b));
                List<ProcessedVisit> storedVisits = this.processedVisitJdbcService.findByUserAndTimeOverlap(user, memory.getStartDate(), memory.getEndDate());
                List<MemoryVisit> currentMemoryVisits = memoryVisitJdbcService.findByMemoryBlockId(blockId);
                List<MemoryVisitDto> availableVisits = new ArrayList<>();
                currentMemoryVisits.stream().map(v -> new MemoryVisitDto(v.getId(), v, true, true)).forEach(availableVisits::add);
                storedVisits.stream().filter(v -> currentMemoryVisits.stream().noneMatch(mv -> mv.getStartTime().equals(v.getStartTime()))).forEach(v -> availableVisits.add(new MemoryVisitDto(v.getId(), MemoryVisit.create(v), false, false)));
                model.addAttribute("availableVisits", availableVisits.stream().sorted(Comparator.comparing(memoryVisitDto -> memoryVisitDto.visit.getStartTime())).toList());
                return "memories/blocks/edit :: edit-cluster-visit-block";
            case CLUSTER_TRIP:
                List<Trip> storedTrips = this.tripJdbcService.findByUserAndTimeOverlap(user, memory.getStartDate(), memory.getEndDate());
                List<MemoryTrip> currentMemoryTrips = memoryTripJdbcService.findByMemoryBlockId(blockId);
                List<MemoryTripDto> availableTrips = new ArrayList<>();
                currentMemoryTrips.stream().map(v -> new MemoryTripDto(v.getId(), v, true, true)).forEach(availableTrips::add);

                storedTrips.stream().filter(v -> currentMemoryTrips.stream().noneMatch(mv -> mv.getStartTime().equals(v.getStartTime()))).forEach(t -> {
                    MemoryVisit startVisit = MemoryVisit.create(t.getStartVisit());
                    MemoryVisit endVisit = MemoryVisit.create(t.getEndVisit());
                    availableTrips.add(new MemoryTripDto(t.getId(), MemoryTrip.create(t, startVisit, endVisit), false, false));
                });

                memoryService.getBlock(user, timezone, memoryId, blockId).ifPresent(b ->
                        model.addAttribute("clusterTripBlock", b));
                model.addAttribute("availableTrips", availableTrips.stream().sorted(Comparator.comparing(memoryTripDto -> memoryTripDto.trip.getStartTime())).toList());
                return "memories/blocks/edit :: edit-cluster-trip-block";
        }

        return "memories/blocks/edit";
    }

    @GetMapping("/{blockId}/view")
    public String viewBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam(required = false, defaultValue = "UTC" ) ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(this.memoryService.getBlock(user, timezone, memoryId, blockId).orElseThrow(() -> new IllegalArgumentException("Block not found"))));
        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));

        return "memories/view :: view-block";
    }


    @PostMapping("/{blockId}/cluster")
    public String updateClusterBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam(name = "selectedParts") List<String> selectedParts,
            @RequestParam(required = false) String title,
            @RequestParam(required = false, defaultValue = "UTC" ) ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        MemoryClusterBlock block = memoryService.getClusterBlock(user, blockId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster block not found"));

        List<Long> partIds = new ArrayList<>();
        if (block.getType() == BlockType.CLUSTER_TRIP) {
            List<MemoryTrip> persistedTrips = this.memoryTripJdbcService.findByMemoryBlockId(blockId);

            for (String selectedPart : selectedParts) {
                String type = selectedPart.substring(0, selectedPart.lastIndexOf("-"));
                long partId = Long.parseLong(selectedPart.substring(selectedPart.lastIndexOf("-") + 4));
                switch (type) {
                    case "m":
                        MemoryTrip knownMemoryTrip = persistedTrips.stream().filter(p -> p.getId() == partId).findFirst().orElseThrow(() -> new IllegalArgumentException("MemoryTrip not found"));
                        partIds.add(knownMemoryTrip.getId());
                        persistedTrips.remove(knownMemoryTrip);
                        break;
                    case "t":
                        Trip trip = this.tripJdbcService.findById(partId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
                        MemoryVisit startVisit = this.memoryVisitJdbcService.save(user, MemoryVisit.create(trip.getStartVisit()), block.getBlockId(), trip.getStartVisit().getId());
                        MemoryVisit endVisit = this.memoryVisitJdbcService.save(user, MemoryVisit.create(trip.getEndVisit()), block.getBlockId(), trip.getEndVisit().getId());
                        MemoryTrip persistedMemoryTrip = this.memoryTripJdbcService.save(user, MemoryTrip.create(trip, startVisit, endVisit), block.getBlockId(), trip.getId());
                        partIds.add(persistedMemoryTrip.getId());
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid part id [" + selectedPart + "] detected");
                }
            }
            persistedTrips.forEach(mt -> memoryTripJdbcService.deleteById(mt.getId()));
        } else if (block.getType() == BlockType.CLUSTER_VISIT) {
            List<MemoryVisit> persistedVisits = this.memoryVisitJdbcService.findByMemoryBlockId(blockId);
            for (String selectedPart : selectedParts) {
                String type = selectedPart.substring(0, selectedPart.lastIndexOf("-"));
                long partId = Long.parseLong(selectedPart.substring(selectedPart.lastIndexOf("-") + 4));
                switch (type) {
                    case "m":
                        MemoryVisit knownMemoryVisit = persistedVisits.stream().filter(p -> p.getId() == partId).findFirst().orElseThrow(() -> new IllegalArgumentException("MemoryVisit not found"));
                        partIds.add(knownMemoryVisit.getId());
                        persistedVisits.remove(knownMemoryVisit);
                        break;
                    case "v":
                        ProcessedVisit visit = this.processedVisitJdbcService.findById(partId).orElseThrow(() -> new IllegalArgumentException("ProcessedVisit not found"));
                        MemoryVisit persistedMemoryVisit = this.memoryVisitJdbcService.save(user, MemoryVisit.create(visit), block.getBlockId(), visit.getId());
                        partIds.add(persistedMemoryVisit.getId());
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid part id [" + selectedPart + "] detected");
                }
            }
            persistedVisits.forEach(mt -> memoryVisitJdbcService.deleteById(mt.getId()));
        } else {
            throw new IllegalArgumentException("Invalid block type [" + block.getType() + "] detected");
        }

        MemoryClusterBlock updated = block.withPartIds(partIds).withTitle(title);
        memoryService.updateClusterBlock(user, updated);

        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(this.memoryService.getBlock(user, timezone, memoryId, blockId).orElseThrow(() -> new IllegalArgumentException("Block not found"))));
        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));
        return "memories/view :: view-block";
    }

    @PostMapping("/cluster")
    public String createClusterBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam(required = false, defaultValue = "-1") int position,
            @RequestParam(name = "selectedParts") List<Long> selectedParts,
            @RequestParam BlockType type,
            @RequestParam(required = false) String title,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        MemoryClusterBlock clusterBlock = memoryService.createClusterBlock(user,memory, title, position, type, selectedParts);
        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(this.memoryService.getBlock(user, timezone, memoryId, clusterBlock.getBlockId()).orElseThrow(() -> new IllegalArgumentException("Block not found"))));
        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));
        return "memories/view :: view-block";
    }


    @PostMapping("/reorder")
    public String reorderBlocks(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam List<Long> blockIds) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        memoryService.reorderBlocks(user, memoryId, blockIds);
        return "redirect:/memories/" + memoryId;
    }

    @PostMapping("/text")
    public String createTextBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam(required = false, defaultValue = "-1") int position,
            @RequestParam(required = false) String headline,
            @RequestParam(required = false) String content,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        MemoryBlock block = memoryService.addBlock(user, memoryId, position, BlockType.TEXT);
        memoryService.addTextBlock(block.getId(), headline, content);
        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(this.memoryService.getBlock(user, timezone, memoryId, block.getId()).orElseThrow(() -> new IllegalArgumentException("Block not found"))));
        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));
        return "memories/view :: view-block";
    }

    @PostMapping("/{blockId}/text")
    public String updateTextBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam String headline,
            @RequestParam String content,
            @RequestParam(required = false, defaultValue = "UTC" ) ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        MemoryBlockText textBlock = memoryService.getTextBlock(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Text block not found"));

        MemoryBlockText updated = textBlock.withHeadline(headline).withContent(content);
        memoryService.updateTextBlock(user, updated);

        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(updated));
        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));
        return "memories/view :: view-block";
    }

    @PostMapping("/image-gallery")
    public String createImageGalleryBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam(required = false, defaultValue = "-1") int position,
            @RequestParam(required = false) List<String> uploadedUrls,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId).orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlock block = memoryService.addBlock(user, memoryId, position, BlockType.IMAGE_GALLERY);
        List<MemoryBlockImageGallery.GalleryImage> imageBlocks = new ArrayList<>();

        if (uploadedUrls != null) {
            for (String url : uploadedUrls) {
                imageBlocks.add(new MemoryBlockImageGallery.GalleryImage(url, null, "upload", null));
            }
        }

        if (imageBlocks.isEmpty()) {
            throw new IllegalArgumentException("No images selected");
        }

        memoryService.addImageGalleryBlock(block.getId(), imageBlocks);
        
        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(memoryService.getBlock(user, timezone, memoryId, block.getId()).orElseThrow(() -> new IllegalArgumentException("Block not found"))));
        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));
        return "memories/view :: view-block";
    }

    @PostMapping("/{blockId}/image-gallery")
    public String updateImageGalleryBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam(required = false) List<String> uploadedUrls,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        MemoryBlockImageGallery imageBlock = memoryService.getImagesForBlock(blockId);
        List<MemoryBlockImageGallery.GalleryImage> imageBlocks = new ArrayList<>();

        if (uploadedUrls != null) {
            for (String url : uploadedUrls) {
                imageBlocks.add(new MemoryBlockImageGallery.GalleryImage(url, null, "upload", null));
            }
        }

        if (imageBlocks.isEmpty()) {
            throw new IllegalArgumentException("No images selected");
        }

        this.memoryService.updateImageBlock(user, imageBlock.withImages(imageBlocks));

        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(memoryService.getBlock(user, timezone, memoryId, blockId).orElseThrow(() -> new IllegalArgumentException("Block not found"))));
        model.addAttribute("isOwner", isOwner(memory, user));
        model.addAttribute("canEdit", canEdit(memory, user));
        return "memories/view :: view-block";
    }

    @PostMapping("/upload-image")
    public String uploadImage(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam("files") List<MultipartFile> files,
            Model model) {

        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        List<String> urls = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }

            try {
                String originalFilename = file.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                String filename = UUID.randomUUID() + extension;

                storageService.store("memories/" + memoryId + "/" + filename, file.getInputStream(), file.getSize(), file.getContentType());

                String fileUrl = "/api/v1/photos/reitti/memories/" + memoryId + "/" + filename;
                urls.add(fileUrl);

            } catch (IOException e) {
                throw new RuntimeException("Failed to upload file", e);
            }
        }

        model.addAttribute("urls", urls);
        return "memories/fragments :: uploaded-photos";
    }

    @PostMapping("/fetch-immich-photo")
    public String fetchImmichPhoto(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam String assetId,
            Model model) {

        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        String imageUrl;
        if (storageService.exists("memories/" + memoryId + "/" + assetId)) {
            imageUrl = "/api/v1/photos/reitti/memories/" + memoryId + "/" + assetId;
        } else {
            String filename = this.immichIntegrationService.downloadImage(user, assetId, "memories/" + memoryId);
            imageUrl = "/api/v1/photos/reitti/memories/" + memoryId + "/" + filename;
        }

        model.addAttribute("urls", List.of(imageUrl));

        return "memories/fragments :: uploaded-photos";
    }

    @GetMapping("/immich-photos")
    public String getImmichPhotos(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "UTC") String timezone,
            Model model) {
        
        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate startDate = memory.getStartDate().atZone(zoneId).toLocalDate();
        LocalDate endDate = memory.getEndDate().atZone(zoneId).toLocalDate();
        
        List<PhotoResponse> allPhotos = immichIntegrationService.searchPhotosForRange(user, startDate, endDate, timezone);
        
        int pageSize = 12;
        int totalPages = (int) Math.ceil((double) allPhotos.size() / pageSize);
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allPhotos.size());
        
        List<PhotoResponse> pagePhotos = allPhotos.subList(startIndex, endIndex);
        
        model.addAttribute("photos", pagePhotos);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("memoryId", memoryId);
        
        return "memories/fragments :: immich-photos-grid";
    }


    private boolean isOwner(Memory memory, User user) {
        if (user.getAuthorities().contains(ADMIN.asAuthority()) || user.getAuthorities().contains(USER.asAuthority())) {
            return this.memoryService.getOwnerId(memory) == user.getId();
        } else {
            return false;
        }
    }

    private boolean canEdit(Memory memory, User user) {
        if (user.getAuthorities().contains(ADMIN.asAuthority()) || user.getAuthorities().contains(USER.asAuthority())) {
            return this.memoryService.getOwnerId(memory) == user.getId();
        } else {
            //assume the user is of type TokenUser
            TokenUser tokenUser = (TokenUser) user;
            return user.getAuthorities().contains(MagicLinkAccessLevel.MEMORY_EDIT_ACCESS.asAuthority()) && tokenUser.grantsAccessTo(MagicLinkResourceType.MEMORY, memory.getId());
        }
    }

    private record MemoryTripDto(long id, MemoryTrip trip, boolean selected, boolean memoryTrip) {
    }

    private record MemoryVisitDto(long id, MemoryVisit visit, boolean selected, boolean memoryVisit) {
    }
}
