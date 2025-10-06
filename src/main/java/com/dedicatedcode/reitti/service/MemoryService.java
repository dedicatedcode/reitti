package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class MemoryService {

    private final MemoryJdbcService memoryJdbcService;
    private final MemoryBlockJdbcService memoryBlockJdbcService;
    private final MemoryBlockVisitJdbcService memoryBlockVisitJdbcService;
    private final MemoryBlockTripJdbcService memoryBlockTripJdbcService;
    private final MemoryBlockTextJdbcService memoryBlockTextJdbcService;
    private final MemoryBlockImageGalleryJdbcService memoryBlockImageGalleryJdbcService;

    public MemoryService(
            MemoryJdbcService memoryJdbcService,
            MemoryBlockJdbcService memoryBlockJdbcService,
            MemoryBlockVisitJdbcService memoryBlockVisitJdbcService,
            MemoryBlockTripJdbcService memoryBlockTripJdbcService,
            MemoryBlockTextJdbcService memoryBlockTextJdbcService,
            MemoryBlockImageGalleryJdbcService memoryBlockImageGalleryJdbcService) {
        this.memoryJdbcService = memoryJdbcService;
        this.memoryBlockJdbcService = memoryBlockJdbcService;
        this.memoryBlockVisitJdbcService = memoryBlockVisitJdbcService;
        this.memoryBlockTripJdbcService = memoryBlockTripJdbcService;
        this.memoryBlockTextJdbcService = memoryBlockTextJdbcService;
        this.memoryBlockImageGalleryJdbcService = memoryBlockImageGalleryJdbcService;
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

    public List<Memory> getMemoriesByDateRange(User user, LocalDate startDate, LocalDate endDate) {
        return memoryJdbcService.findByDateRange(user, startDate, endDate);
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

    public Optional<MemoryBlock> getBlockById(Long blockId) {
        return memoryBlockJdbcService.findById(blockId);
    }

    @Transactional
    public MemoryBlockVisit addVisitBlock(Long blockId, Long visitId) {
        MemoryBlockVisit blockVisit = new MemoryBlockVisit(blockId, visitId);
        return memoryBlockVisitJdbcService.create(blockVisit);
    }

    public Optional<MemoryBlockVisit> getVisitBlock(Long blockId) {
        return memoryBlockVisitJdbcService.findByBlockId(blockId);
    }

    @Transactional
    public MemoryBlockTrip addTripBlock(Long blockId, Long tripId) {
        MemoryBlockTrip blockTrip = new MemoryBlockTrip(blockId, tripId);
        return memoryBlockTripJdbcService.create(blockTrip);
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
}
