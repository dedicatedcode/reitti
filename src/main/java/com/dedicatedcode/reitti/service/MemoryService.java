package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MemoryService {
    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryJdbcService memoryJdbcService;
    private final MemoryBlockJdbcService memoryBlockJdbcService;
    private final MemoryBlockVisitJdbcService memoryBlockVisitJdbcService;
    private final MemoryBlockTripJdbcService memoryBlockTripJdbcService;
    private final MemoryBlockTextJdbcService memoryBlockTextJdbcService;
    private final MemoryBlockImageGalleryJdbcService memoryBlockImageGalleryJdbcService;
    private final MemoryBlockGenerationService blockGenerationService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;

    public MemoryService(
            MemoryJdbcService memoryJdbcService,
            MemoryBlockJdbcService memoryBlockJdbcService,
            MemoryBlockVisitJdbcService memoryBlockVisitJdbcService,
            MemoryBlockTripJdbcService memoryBlockTripJdbcService,
            MemoryBlockTextJdbcService memoryBlockTextJdbcService,
            MemoryBlockImageGalleryJdbcService memoryBlockImageGalleryJdbcService,
            MemoryBlockGenerationService blockGenerationService,
            ProcessedVisitJdbcService processedVisitJdbcService,
            TripJdbcService tripJdbcService) {
        this.memoryJdbcService = memoryJdbcService;
        this.memoryBlockJdbcService = memoryBlockJdbcService;
        this.memoryBlockVisitJdbcService = memoryBlockVisitJdbcService;
        this.memoryBlockTripJdbcService = memoryBlockTripJdbcService;
        this.memoryBlockTextJdbcService = memoryBlockTextJdbcService;
        this.memoryBlockImageGalleryJdbcService = memoryBlockImageGalleryJdbcService;
        this.blockGenerationService = blockGenerationService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
    }

    @Transactional
    public Memory createMemory(User user, Memory memory) {
        return memoryJdbcService.create(user, memory);
    }

    @Transactional
    public Memory updateMemory(User user, Memory memory) {
        return memoryJdbcService.update(user, memory);
    }

    @Transactional
    public void deleteMemory(User user, Long memoryId) {
        memoryJdbcService.delete(user, memoryId);
    }

    public Optional<Memory> getMemoryById(User user, Long id) {
        return memoryJdbcService.findById(user, id);
    }

    public List<Memory> getMemoriesForUser(User user) {
        return memoryJdbcService.findAllByUser(user);
    }

    @Transactional
    public MemoryBlock addBlock(Long memoryId, BlockType blockType) {
        int maxPosition = memoryBlockJdbcService.getMaxPosition(memoryId);
        MemoryBlock block = new MemoryBlock(memoryId, blockType, maxPosition + 1);
        return memoryBlockJdbcService.create(block);
    }

    @Transactional
    public void deleteBlock(Long blockId) {
        memoryBlockJdbcService.delete(blockId);
    }

    @Transactional
    public MemoryBlock updateBlockPosition(MemoryBlock block, Integer newPosition) {
        MemoryBlock updatedBlock = block.withPosition(newPosition);
        return memoryBlockJdbcService.update(updatedBlock);
    }

    public List<MemoryBlock> getBlocksForMemory(Long memoryId) {
        return memoryBlockJdbcService.findByMemoryId(memoryId);
    }

    public List<MemoryBlockPart> getBlockPartsForMemory(Long memoryId) {
        List<MemoryBlock> blocks = memoryBlockJdbcService.findByMemoryId(memoryId);
        List<MemoryBlockPart> blockParts = new ArrayList<>();
        
        for (MemoryBlock block : blocks) {
            switch (block.getBlockType()) {
                case TEXT:
                    memoryBlockTextJdbcService.findByBlockId(block.getId())
                        .ifPresent(blockParts::add);
                    break;
                case VISIT:
                    memoryBlockVisitJdbcService.findByBlockId(block.getId())
                        .ifPresent(blockParts::add);
                    break;
                case TRIP:
                    memoryBlockTripJdbcService.findByBlockId(block.getId())
                        .ifPresent(blockParts::add);
                    break;
                case IMAGE_GALLERY:
                    List<MemoryBlockImageGallery> galleryImages = memoryBlockImageGalleryJdbcService.findByBlockId(block.getId());
                    blockParts.addAll(galleryImages);
                    break;
            }
        }
        
        return blockParts;
    }

    public Optional<MemoryBlock> getBlockById(Long blockId) {
        return memoryBlockJdbcService.findById(blockId);
    }

    @Transactional
    public MemoryBlockVisit addVisitBlock(User user, Long blockId, Long visitId) {
        ProcessedVisit visit = this.processedVisitJdbcService.findByUserAndId(user, visitId).orElseThrow(() -> new IllegalArgumentException("Visit not found"));
        MemoryBlockVisit blockWithId = new MemoryBlockVisit(
            blockId,
                visit.getId(),
                visit.getPlace().getName(),
                visit.getPlace().getAddress(),
                visit.getPlace().getLatitudeCentroid(),
                visit.getPlace().getLongitudeCentroid(),
                visit.getStartTime(),
                visit.getEndTime(),
                visit.getDurationSeconds()
        );
        return memoryBlockVisitJdbcService.create(blockWithId);
    }

    public Optional<MemoryBlockVisit> getVisitBlock(Long blockId) {
        return memoryBlockVisitJdbcService.findByBlockId(blockId);
    }

    @Transactional
    public MemoryBlockTrip addTripBlock(User user, Long blockId, Long tripId) {
        Trip trip = this.tripJdbcService.findByUserAndId(user, tripId).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        MemoryBlockTrip blockWithId = new MemoryBlockTrip(
            blockId,
                trip.getStartTime(),
                trip.getEndTime(),
                trip.getDurationSeconds(),
                trip.getEstimatedDistanceMeters(),
                trip.getTravelledDistanceMeters(),
                trip.getTransportModeInferred(),
                trip.getStartVisit().getPlace().getName(),
                trip.getStartVisit().getPlace().getLatitudeCentroid(),
                trip.getStartVisit().getPlace().getLongitudeCentroid(),
                trip.getEndVisit().getPlace().getName(),
                trip.getEndVisit().getPlace().getLatitudeCentroid(),
                trip.getEndVisit().getPlace().getLongitudeCentroid()
        );
        return memoryBlockTripJdbcService.create(blockWithId);
    }

    public Optional<MemoryBlockTrip> getTripBlock(Long blockId) {
        return memoryBlockTripJdbcService.findByBlockId(blockId);
    }

    @Transactional
    public MemoryBlockText addTextBlock(Long blockId, String headline, String content) {
        MemoryBlockText blockText = new MemoryBlockText(blockId, headline, content);
        return memoryBlockTextJdbcService.create(blockText);
    }

    @Transactional
    public MemoryBlockText updateTextBlock(MemoryBlockText blockText) {
        return memoryBlockTextJdbcService.update(blockText);
    }

    public Optional<MemoryBlockText> getTextBlock(Long blockId) {
        return memoryBlockTextJdbcService.findByBlockId(blockId);
    }

    @Transactional
    public MemoryBlockImageGallery addImageToGallery(Long blockId, String imageUrl, String caption) {
        int maxPosition = memoryBlockImageGalleryJdbcService.findByBlockId(blockId).size();
        MemoryBlockImageGallery image = new MemoryBlockImageGallery(blockId, imageUrl, caption, maxPosition);
        return memoryBlockImageGalleryJdbcService.create(image);
    }

    @Transactional
    public MemoryBlockImageGallery updateImageInGallery(MemoryBlockImageGallery image) {
        return memoryBlockImageGalleryJdbcService.update(image);
    }

    @Transactional
    public void deleteImageFromGallery(Long imageId) {
        memoryBlockImageGalleryJdbcService.delete(imageId);
    }

    public List<MemoryBlockImageGallery> getImagesForBlock(Long blockId) {
        return memoryBlockImageGalleryJdbcService.findByBlockId(blockId);
    }

    @Transactional
    public void reorderBlocks(Long memoryId, List<Long> blockIds) {
        for (int i = 0; i < blockIds.size(); i++) {
            Long blockId = blockIds.get(i);
            Optional<MemoryBlock> blockOpt = memoryBlockJdbcService.findById(blockId);
            if (blockOpt.isPresent()) {
                MemoryBlock block = blockOpt.get();
                if (!block.getMemoryId().equals(memoryId)) {
                    throw new IllegalArgumentException("Block does not belong to this memory");
                }
                if (!block.getPosition().equals(i)) {
                    memoryBlockJdbcService.update(block.withPosition(i));
                }
            }
        }
    }

    @Transactional
    public void recalculateMemory(User user, Long memoryId) {
        Memory memory = memoryJdbcService.findById(user, memoryId).orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        // Delete all existing blocks
        memoryBlockJdbcService.deleteByMemoryId(memoryId);
        
        // Generate new blocks
        List<MemoryBlockPart> autoGeneratedBlocks = blockGenerationService.generate(user, memory);
        
        // Save the generated blocks
        for (MemoryBlockPart autoGeneratedBlock : autoGeneratedBlocks) {
            if (autoGeneratedBlock instanceof MemoryBlockText textBlock) {
                MemoryBlock memoryBlock = addBlock(memoryId, BlockType.TEXT);
                memoryBlockTextJdbcService.create(new MemoryBlockText(memoryBlock.getId(), textBlock.getHeadline(), textBlock.getContent()));
            } else if (autoGeneratedBlock instanceof MemoryBlockImageGallery imageGalleryBlock) {
                MemoryBlock memoryBlock = addBlock(memoryId, BlockType.IMAGE_GALLERY);
                memoryBlockImageGalleryJdbcService.create(imageGalleryBlock.withId(null));
            } else if (autoGeneratedBlock instanceof MemoryBlockTrip tripBlock) {
                MemoryBlock memoryBlock = addBlock(memoryId, BlockType.TRIP);
                memoryBlockTripJdbcService.create(new MemoryBlockTrip(
                    memoryBlock.getId(),
                    tripBlock.getStartTime(),
                    tripBlock.getEndTime(),
                    tripBlock.getDurationSeconds(),
                    tripBlock.getEstimatedDistanceMeters(),
                    tripBlock.getTravelledDistanceMeters(),
                    tripBlock.getTransportModeInferred(),
                    tripBlock.getStartPlaceName(),
                    tripBlock.getStartLatitude(),
                    tripBlock.getStartLongitude(),
                    tripBlock.getEndPlaceName(),
                    tripBlock.getEndLatitude(),
                    tripBlock.getEndLongitude()
                ));
            } else if (autoGeneratedBlock instanceof MemoryBlockVisit visitBlock) {
                MemoryBlock memoryBlock = addBlock(memoryId, BlockType.VISIT);
                memoryBlockVisitJdbcService.create(new MemoryBlockVisit(
                    memoryBlock.getId(),
                    visitBlock.getOriginalProcessedVisitId(),
                    visitBlock.getPlaceName(),
                    visitBlock.getPlaceAddress(),
                    visitBlock.getLatitude(),
                    visitBlock.getLongitude(),
                    visitBlock.getStartTime(),
                    visitBlock.getEndTime(),
                    visitBlock.getDurationSeconds()
                ));
            }
        }
        
        log.info("Recalculated memory {} with {} blocks", memoryId, autoGeneratedBlocks.size());
    }
}
