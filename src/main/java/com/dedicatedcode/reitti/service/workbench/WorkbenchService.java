package com.dedicatedcode.reitti.service.workbench;

import com.dedicatedcode.reitti.dto.workbench.WorkbenchCommitRequest;
import com.dedicatedcode.reitti.model.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkbenchService {
    private static final Logger log = LoggerFactory.getLogger(WorkbenchService.class);
    public void applyCommit(User user, WorkbenchCommitRequest request) {
        log.debug("Applying commit {}", request);
    }
}
