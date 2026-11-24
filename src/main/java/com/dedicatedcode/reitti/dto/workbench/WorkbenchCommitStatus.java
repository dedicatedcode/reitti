package com.dedicatedcode.reitti.dto.workbench;

public record WorkbenchCommitStatus(boolean done, boolean success, String state, long activeJobs, long failedJobs) {
}
