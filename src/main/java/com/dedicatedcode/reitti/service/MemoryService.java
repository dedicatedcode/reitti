package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class MemoryService {
    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryJdbcService memoryJdbcService;
    private final MemoryBlockJdbcService memoryBlockJdbcService;
    private final MemoryBlockTextJdbcService memoryBlockTextJdbcService;
    private final MemoryBlockImageGalleryJdbcService memoryBlockImageGalleryJdbcService;
    private final MemoryClusterBlockRepository memoryClusterBlockRepository;
    private final MemoryBlockGenerationService blockGenerationService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;

    public MemoryService(
            MemoryJdbcService memoryJdbcService,
            MemoryBlockJdbcService memoryBlockJdbcService,
            MemoryBlockTextJdbcService memoryBlockTextJdbcService,
            MemoryBlockImageGalleryJdbcService memoryBlockImageGalleryJdbcService,
            MemoryClusterBlockRepository memoryClusterBlockRepository,
            MemoryBlockGenerationService blockGenerationService,
            ProcessedVisitJdbcService processedVisitJdbcService,
            TripJdbcService tripJdbcService,
            UserSettingsJdbcService userSettingsJdbcService) {
        this.memoryJdbcService = memoryJdbcService;
        this.memoryBlockJdbcService = memoryBlockJdbcService;
        this.memoryBlockTextJdbcService = memoryBlockTextJdbcService;
        this.memoryBlockImageGalleryJdbcService = memoryBlockImageGalleryJdbcService;
        this.memoryClusterBlockRepository = memoryClusterBlockRepository;
        this.blockGenerationService = blockGenerationService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
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
    public MemoryBlock addBlock(User user, Long memoryId, int position, BlockType blockType) {
        Memory memory = this.memoryJdbcService.findById(user, memoryId).orElseThrow(() -> new IllegalArgumentException("Unable to find memory with id [" + memoryId + "]"));
        int maxPosition = memoryBlockJdbcService.getMaxPosition(memoryId);

        MemoryBlock block = new MemoryBlock(memoryId, blockType, maxPosition + 1);
        block = memoryBlockJdbcService.create(block);
        if (position > -1) {
            List<MemoryBlock> list = memoryBlockJdbcService.findByMemoryId(memoryId);
            MemoryBlock lastBlock = list.removeLast();
            list.add(position, lastBlock);
            reorderBlocks(user, memoryId, list.stream().map(MemoryBlock::getId).toList());
        }
        return block;
    }

    @Transactional
    public void deleteBlock(Long blockId) {
        memoryBlockJdbcService.delete(blockId);
    }

    public List<MemoryBlockPart> getBlockPartsForMemory(User user, Long memoryId, ZoneId timezone) {
        UserSettings settings = this.userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        List<MemoryBlock> blocks = memoryBlockJdbcService.findByMemoryId(memoryId);
        List<MemoryBlockPart> blockParts = new ArrayList<>();
        
        for (MemoryBlock block : blocks) {
            Optional<? extends MemoryBlockPart> part = loadAndConvertBlockInstance(user, timezone, block, settings);
            part.ifPresent(blockParts::add);
        }
        return blockParts;
    }

    public Optional<? extends MemoryBlockPart> getBlock(User user, ZoneId timezone, long memoryId, long blockId) {
        UserSettings settings = this.userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        Optional<MemoryBlock> blockOpt = memoryBlockJdbcService.findById(user, blockId);
        if (blockOpt.isPresent()) {
            MemoryBlock block = blockOpt.get();
            if (!block.getMemoryId().equals(memoryId)) {
                throw new IllegalArgumentException("Block does not belong to this memory");
            }
            return loadAndConvertBlockInstance(user, timezone, block, settings);
        } else {
            return Optional.empty();
        }
    }
    private Optional<? extends MemoryBlockPart> loadAndConvertBlockInstance(User user, ZoneId timezone, MemoryBlock block, UserSettings settings) {
        return switch (block.getBlockType()) {
            case TEXT -> memoryBlockTextJdbcService.findByBlockId(block.getId());
            case IMAGE_GALLERY -> memoryBlockImageGalleryJdbcService.findByBlockId(block.getId());
            case CLUSTER_TRIP -> getClusterTripBlock(user, timezone, block, settings);
            case CLUSTER_VISIT -> getClusterVisitBlock(user, timezone, block, settings);
        };
    }

    public Optional<MemoryBlock> getBlockById(User user, Long blockId) {
        return memoryBlockJdbcService.findById(user, blockId);
    }

    public Optional<MemoryClusterBlock> getClusterBlock(User user, Long blockId) {
        return memoryClusterBlockRepository.findByBlockId(user, blockId);
    }

    @Transactional
    public MemoryBlockText addTextBlock(Long blockId, String headline, String content) {
        MemoryBlockText blockText = new MemoryBlockText(blockId, headline, content);
        return memoryBlockTextJdbcService.create(blockText);
    }

    @Transactional
    public MemoryBlockText updateTextBlock(User user, MemoryBlockText blockText) {
        return memoryBlockTextJdbcService.update(blockText);
    }

    @Transactional
    public void createClusterBlock(User user, MemoryClusterBlock clusterBlock) {
        memoryClusterBlockRepository.save(user, clusterBlock);
    }

    @Transactional
    public MemoryClusterBlock updateClusterBlock(User user, MemoryClusterBlock clusterBlock) {
        return memoryClusterBlockRepository.update(user, clusterBlock);
    }

    public Optional<MemoryBlockText> getTextBlock(Long blockId) {
        return memoryBlockTextJdbcService.findByBlockId(blockId);
    }

    @Transactional
    public MemoryBlockImageGallery addImageGalleryBlock(Long blockId, List<MemoryBlockImageGallery.GalleryImage> images) {
        return this.memoryBlockImageGalleryJdbcService.create(new MemoryBlockImageGallery(blockId, images));

    }
    @Transactional
    public void deleteImageFromGallery(Long imageId) {
        memoryBlockImageGalleryJdbcService.delete(imageId);
    }

    public MemoryBlockImageGallery getImagesForBlock(Long blockId) {
        return memoryBlockImageGalleryJdbcService.findByBlockId(blockId).orElseThrow(() -> new IllegalArgumentException("Block not found"));
    }

    @Transactional
    public void reorderBlocks(User user, Long memoryId, List<Long> blockIds) {
        // First, temporarily shift all positions to avoid unique constraint violations
        List<MemoryBlock> allBlocks = memoryBlockJdbcService.findByMemoryId(memoryId);
        int offset = blockIds.size() + 1; // Use an offset larger than the number of blocks
        for (MemoryBlock block : allBlocks) {
            memoryBlockJdbcService.update(block.withPosition(block.getPosition() + offset));
        }

        // Now, set the correct positions
        for (int i = 0; i < blockIds.size(); i++) {
            Long blockId = blockIds.get(i);
            Optional<MemoryBlock> blockOpt = memoryBlockJdbcService.findById(user, blockId);

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
    public void recalculateMemory(User user, Long memoryId, ZoneId timezone) {
        Memory memory = memoryJdbcService.findById(user, memoryId).orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        // Delete all existing blocks
        memoryBlockJdbcService.deleteByMemoryId(memoryId);
        
        // Generate new blocks
        List<MemoryBlockPart> autoGeneratedBlocks = blockGenerationService.generate(user, memory, timezone);

        // Save the generated blocks
        for (MemoryBlockPart autoGeneratedBlock : autoGeneratedBlocks) {
            if (autoGeneratedBlock instanceof MemoryBlockText textBlock) {
                MemoryBlock memoryBlock = addBlock(user, memoryId, -1, BlockType.TEXT);
                memoryBlockTextJdbcService.create(new MemoryBlockText(memoryBlock.getId(), textBlock.getHeadline(), textBlock.getContent()));
            } else if (autoGeneratedBlock instanceof MemoryBlockImageGallery imageGalleryBlock) {
                MemoryBlock memoryBlock = addBlock(user, memoryId, -1, BlockType.IMAGE_GALLERY);
                memoryBlockImageGalleryJdbcService.create(new MemoryBlockImageGallery(memoryBlock.getId(), imageGalleryBlock.getImages()));
            } else if (autoGeneratedBlock instanceof MemoryClusterBlock clusterBlock) {
                MemoryBlock memoryBlock = addBlock(user, memoryId, -1, clusterBlock.getType());
                memoryClusterBlockRepository.save(user, new MemoryClusterBlock(memoryBlock.getId(),
                        clusterBlock.getPartIds(),
                        clusterBlock.getTitle(),
                        clusterBlock.getDescription(), clusterBlock.getType()));
            }
        }
        
        log.info("Recalculated memory {} with {} blocks", memoryId, autoGeneratedBlocks.size());
    }


    private Optional<? extends MemoryBlockPart> getClusterTripBlock(User user, ZoneId timezone, MemoryBlock block, UserSettings settings) {
        Optional<? extends MemoryBlockPart> part;
        Optional<MemoryClusterBlock> clusterBlockOpt = memoryClusterBlockRepository.findByBlockId(user, block.getId());
        part = clusterBlockOpt.map(memoryClusterBlock -> {
            List<Trip> trips = tripJdbcService.findByIds(user, memoryClusterBlock.getPartIds());
            Optional<Trip> first = trips.stream().findFirst();
            Optional<Trip> lastTrip = trips.stream().max(Comparator.comparing(Trip::getEndTime));

            long movingTime = trips.stream().mapToLong(Trip::getDurationSeconds).sum();
            long completeTime = first.map(trip -> Duration.between(trip.getStartTime(), lastTrip.get().getEndTime()).toSeconds()).orElse(0L);
            LocalDateTime adjustedStartTime = first.map(t -> adjustTime(settings, t.getStartTime(), t.getStartVisit().getPlace(), timezone)).orElse(null);
            LocalDateTime adjustedEndTime = lastTrip.map(t -> adjustTime(settings, t.getEndTime(), t.getEndVisit().getPlace(), timezone)).orElse(null);
            return new MemoryTripClusterBlockDTO(
                    memoryClusterBlock,
                    trips,
                    "/api/v1/raw-location-points/trips?trips=" + String.join(",", memoryClusterBlock.getPartIds().stream().map(Objects::toString).toList()),
                    adjustedStartTime,
                    adjustedEndTime,
                    completeTime,
                    movingTime);
        });
        return part;
    }

    private Optional<? extends MemoryBlockPart> getClusterVisitBlock(User user, ZoneId timezone, MemoryBlock block, UserSettings settings) {
        Optional<? extends MemoryBlockPart> part;
        Optional<MemoryClusterBlock> clusterVisitBlockOpt = memoryClusterBlockRepository.findByBlockId(user, block.getId());
        part = clusterVisitBlockOpt.map(memoryClusterBlock -> {
            List<ProcessedVisit> visits = processedVisitJdbcService.findByIds(user, memoryClusterBlock.getPartIds());
            Optional<ProcessedVisit> first = visits.stream().findFirst();
            Optional<ProcessedVisit> last = visits.stream().max(Comparator.comparing(ProcessedVisit::getEndTime));

            LocalDateTime adjustedStartTime = first.map(t -> adjustTime(settings, t.getStartTime(), t.getPlace(), timezone)).orElse(null);
            LocalDateTime adjustedEndTime = last.map(t -> adjustTime(settings, t.getEndTime(), t.getPlace(), timezone)).orElse(null);
            Long completeDuration = 0L;
            return new MemoryVisitClusterBlockDTO(
                    memoryClusterBlock,
                    visits,
                    "/api/v1/raw-location-points?startDate=" + first.get().getStartTime().atZone(timezone).toLocalDateTime() + "&endDate=" + last.get().getEndTime().atZone(timezone).toLocalDateTime() + "&timezone=" + timezone,
                    adjustedStartTime,
                    adjustedEndTime,
                    completeDuration);
        });
        return part;
    }

    private LocalDateTime adjustTime(UserSettings settings, Instant startTime, SignificantPlace place, ZoneId timezone) {
        if (settings.getTimeDisplayMode() == TimeDisplayMode.DEFAULT) {
            return startTime.atZone(timezone).toLocalDateTime();
        } else {
            return startTime.atZone(place.getTimezone()).toLocalDateTime();
        }
    }

}
