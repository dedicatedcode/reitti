package com.dedicatedcode.reitti.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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

    public S3Object read(String itemName) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(itemName)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest);
        GetObjectResponse metadata = response.response();

        return new S3Object(
                response,
                metadata.contentType(),
                metadata.contentLength()
        );
    }

    //create a method to check for the existance of an item by name AI!

    public static class S3Object {
        private final InputStream inputStream;
        private final String contentType;
        private final Long contentLength;

        public S3Object(InputStream inputStream, String contentType, Long contentLength) {
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
