package com.dedicatedcode.reitti.dto.workbench;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkbenchCommitResponse {
    private final boolean success;
    private final String message;
    private final String errorCode;

    public WorkbenchCommitResponse(boolean success, String message) {
        this(success, message, null);
    }

    public WorkbenchCommitResponse(boolean success, String message, String errorCode) {
        this.success = success;
        this.message = message;
        this.errorCode = errorCode;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getErrorCode() { return errorCode; }
}