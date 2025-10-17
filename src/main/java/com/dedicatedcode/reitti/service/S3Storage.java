package com.dedicatedcode.reitti.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
public class S3Storage {
    private final S3Client s3Client;
    private final String bucketName;

    public S3Storage(S3Client s3Client, @Value("${reitti.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public void store(String itemName, InputStream content, long contentLength, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder().bucket(bucketName)
                .key(itemName)
                .contentType(contentType)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(content, contentLength));
    }

    //create a read method to load a file out of the storage including the content-type and length to be served by a endpoint. If possible make it so that we do not need to keep the whole file in memory. It should be used in ReittiPhotoApiController. AI!
}
