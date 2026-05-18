package com.dedicatedcode.reitti.service;

import java.io.Serializable;
import java.util.UUID;

public abstract class JobContext<T> implements Serializable {
    protected final UUID jobId;
    protected final UUID parentJobId;

    protected JobContext() {
        this(null, null);
    }
    protected JobContext(UUID jobId, UUID parentJobId) {
        this.jobId = jobId;
        this.parentJobId = parentJobId;
    }

    public abstract T withJobId(UUID jobId);
    public abstract T withParentJobId(UUID parentJobId);

    public UUID getJobId() {
        return this.jobId;
    }

    public UUID getParentJobId() {
        return this.parentJobId;
    }
}
