package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.controller.error.ForbiddenException;
import com.dedicatedcode.reitti.dto.workbench.WorkbenchCommitRequest;
import com.dedicatedcode.reitti.dto.workbench.WorkbenchCommitResponse;
import com.dedicatedcode.reitti.dto.workbench.WorkbenchCommitStatus;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.workbench.WorkbenchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

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
            UUID jobId = workbenchService.applyCommit(user, request);
            return ResponseEntity.ok(new WorkbenchCommitResponse(true, i18n.translate("workbench.commit.success"), jobId));
        } catch (Exception e) {
            return ResponseEntity.ok(new WorkbenchCommitResponse(false, i18n.translate("workbench.commit.failure", e.getMessage())));
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<WorkbenchCommitStatus> commitStatus(@AuthenticationPrincipal User user,
                                                              @PathVariable UUID jobId) {
        try {
            Optional<WorkbenchCommitStatus> status = workbenchService.getCommitStatus(user, jobId);
            return status.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).build();
        }
    }
}