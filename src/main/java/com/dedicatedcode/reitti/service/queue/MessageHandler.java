package com.dedicatedcode.reitti.service.queue;

@FunctionalInterface
public interface MessageHandler<T> {
    void handle(T payload) throws Exception;
}