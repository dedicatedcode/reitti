package com.dedicatedcode.reitti.dto.workbench;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkbenchCommitResponse {
    private final boolean success;
    private final String message;
    private final String errorCode;
    private final UUID jobId;

    public WorkbenchCommitResponse(boolean success, String message) {
        this(success, message, null, null);
    }

    public WorkbenchCommitResponse(boolean success, String message, UUID jobId) {
        this(success, message, null, jobId);
    }

    public WorkbenchCommitResponse(boolean success, String message, String errorCode) {
        this(success, message, errorCode, null);
    }

    public WorkbenchCommitResponse(boolean success, String message, String errorCode, UUID jobId) {
        this.success = success;
        this.message = message;
        this.errorCode = errorCode;
        this.jobId = jobId;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getErrorCode() { return errorCode; }
    public UUID getJobId() { return jobId; }
}