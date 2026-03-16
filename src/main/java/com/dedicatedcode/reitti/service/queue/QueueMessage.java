package com.dedicatedcode.reitti.service.queue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;

public class QueueMessage<T> implements Serializable {
    private final String id;
    private final T payload;
    private final Instant enqueuedAt;
    private final String queueName;
    private final int retryCount;
    private final String originalQueue;

    @JsonCreator
    public QueueMessage(@JsonProperty("id") String id,
                        @JsonProperty("payload") T payload,
                        @JsonProperty("enqueuedAt") Instant enqueuedAt,
                        @JsonProperty("queueName") String queueName,
                        @JsonProperty("retryCount") int retryCount,
                        @JsonProperty("originalQueue") String originalQueue) {
        this.id = id;
        this.payload = payload;
        this.enqueuedAt = enqueuedAt;
        this.queueName = queueName;
        this.retryCount = retryCount;
        this.originalQueue = originalQueue;
    }

    public String getId() {
        return id;
    }

    public T getPayload() {
        return payload;
    }

    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public String getQueueName() {
        return queueName;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getOriginalQueue() {
        return originalQueue;
    }


    public QueueMessage<T> withRetryCount(int newRetryCount) {
        return new QueueMessage<>(
                this.id,
                this.payload,
                this.enqueuedAt,
                this.queueName,
                newRetryCount,
                this.originalQueue
        );
    }
}
