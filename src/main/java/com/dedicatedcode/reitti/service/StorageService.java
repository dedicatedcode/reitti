package com.dedicatedcode.reitti.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
public class StorageService {
    private final S3Client s3Client;
    private final String bucketName;

    public StorageService(S3Client s3Client, @Value("${reitti.storage.path}") String bucketName) {
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

    public StorageContent read(String itemName) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(itemName)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest);
        GetObjectResponse metadata = response.response();

        return new StorageContent(
                response,
                metadata.contentType(),
                metadata.contentLength()
        );
    }

    public boolean exists(String itemName) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(itemName)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    public static class StorageContent {
        private final InputStream inputStream;
        private final String contentType;
        private final Long contentLength;

        public StorageContent(InputStream inputStream, String contentType, Long contentLength) {
            this.inputStream = inputStream;
            this.contentType = contentType;
            this.contentLength = contentLength;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public String getContentType() {
            return contentType;
        }

        public Long getContentLength() {
            return contentLength;
        }
    }
}
