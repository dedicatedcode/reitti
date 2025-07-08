package com.dedicatedcode.reitti.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ImportStateHolder {
    private final AtomicBoolean importRunning = new AtomicBoolean(false);

    public void importStarted() {
        importRunning.set(true);
    }

    public boolean isImportRunning() {
        return importRunning.get();
    }

    public void importFinished() {
        importRunning.set(false);
    }
}
