package com.dedicatedcode.reitti.dto.workbench;

import java.util.List;

public class EditStoreDto {
    private List<PatchDto> patches;
    private List<DeletedPointDto> deletedPoints;
    private List<MovedPointDto> movedPoints;

    public List<PatchDto> getPatches() {
        return patches;
    }

    public void setPatches(List<PatchDto> patches) {
        this.patches = patches;
    }

    public List<DeletedPointDto> getDeletedPoints() {
        return deletedPoints;
    }

    public void setDeletedPoints(List<DeletedPointDto> deletedPoints) {
        this.deletedPoints = deletedPoints;
    }

    public List<MovedPointDto> getMovedPoints() {
        return movedPoints;
    }

    public void setMovedPoints(List<MovedPointDto> movedPoints) {
        this.movedPoints = movedPoints;
    }
}