package com.dedicatedcode.reitti.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;

@Service
public class S3Storage {
    private final S3Client s3Client;
    private final String bucketName;

    public S3Storage(S3Client s3Client, @Value("${reitti.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }
}
