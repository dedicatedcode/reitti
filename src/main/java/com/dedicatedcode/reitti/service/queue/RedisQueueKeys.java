package com.dedicatedcode.reitti.service.queue;

public class RedisQueueKeys {
    private final String prefix;

    public RedisQueueKeys(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            this.prefix = "";
        } else {
            this.prefix = prefix.endsWith(":") ? prefix : prefix + ":";
        }
    }

    // Main queue
    public String queueKey(String queueName) {
        return prefix + "queue:" + queueName;
    }

    // Processing queue (for in-flight messages)
    public String processingKey(String queueName) {
        return prefix + "processing:" + queueName;
    }

    // Dead letter queue
    public String deadLetterKey(String queueName) {
        return prefix + "dlq:" + queueName;
    }

    // Retry count hash
    public String retryCountKey(String queueName) {
        return prefix + "retry:" + queueName;
    }

    // Queue metadata
    public String metadataKey(String queueName) {
        return prefix + "meta:" + queueName;
    }
}
