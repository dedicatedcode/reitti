package com.dedicatedcode.reitti.dto.workbench;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkbenchCommitResponse {
    private final boolean success;
    private final String message;       // human‑readable, already translated if possible
    private final String errorCode;     // optional, for programmatic use

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