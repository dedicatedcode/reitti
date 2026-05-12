package com.dedicatedcode.reitti.dto.workbench;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkbenchCommitRequest {

    private EditStoreDto editStore;
    private List<ActionDto> actions;
    private FinalStateDto finalState;

    public EditStoreDto getEditStore() {
        return editStore;
    }

    public void setEditStore(EditStoreDto editStore) {
        this.editStore = editStore;
    }

    public List<ActionDto> getActions() {
        return actions;
    }

    public void setActions(List<ActionDto> actions) {
        this.actions = actions;
    }

    public FinalStateDto getFinalState() {
        return finalState;
    }

    public void setFinalState(FinalStateDto finalState) {
        this.finalState = finalState;
    }
}