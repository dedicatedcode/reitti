package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.service.QueueStats;
import com.dedicatedcode.reitti.service.QueueStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/queue-stats")
public class QueueStatsApiController {

    private final QueueStatsService queueStatsService;

    public QueueStatsApiController(QueueStatsService queueStatsService) {
        this.queueStatsService = queueStatsService;
    }

    @GetMapping
    public ResponseEntity<List<QueueStats>> getQueueStats() {
        return ResponseEntity.ok(queueStatsService.getQueueStats());
    }
}
