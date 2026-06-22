package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.dto.workbench.WorkbenchCommitRequest;
import com.dedicatedcode.reitti.dto.workbench.WorkbenchCommitResponse;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.workbench.WorkbenchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/workbench")
public class WorkbenchApiController {
    private final WorkbenchService workbenchService;
    private final I18nService i18n;
    public WorkbenchApiController(WorkbenchService workbenchService, I18nService i18n) {
        this.workbenchService = workbenchService;
        this.i18n = i18n;
    }

    @PostMapping("/commit")
    public ResponseEntity<WorkbenchCommitResponse> commit(@AuthenticationPrincipal User user,
                                                          @RequestBody WorkbenchCommitRequest request) {
        try {
            workbenchService.applyCommit(user, request);
            return ResponseEntity.ok(new WorkbenchCommitResponse(true, i18n.translate("workbench.commit.success")));
        } catch (Exception e) {
            return ResponseEntity.ok(new WorkbenchCommitResponse(false, i18n.translate("workbench.commit.failure", e.getMessage())));
        }
    }
}