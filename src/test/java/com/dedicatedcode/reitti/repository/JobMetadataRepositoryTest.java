package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

@IntegrationTest
class JobMetadataRepositoryTest {
    @Autowired
    private JobMetadataRepository jobMetadataRepository;

    @Test
    void shouldReturnEmptyOptional() {
        Optional<JobMetadataRepository.JobMetadata> byId = this.jobMetadataRepository.findById(UUID.randomUUID());
        assertFalse(byId.isPresent());
    }
}