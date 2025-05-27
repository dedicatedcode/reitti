package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.service.QueueStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/queue-stats")
public class QueueStatsApiController {

    private final QueueStatsService queueStatsService;

    public QueueStatsApiController(QueueStatsService queueStatsService) {
        this.queueStatsService = queueStatsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getQueueStats() {
        return ResponseEntity.ok(queueStatsService.getQueueStats());
    }
}
