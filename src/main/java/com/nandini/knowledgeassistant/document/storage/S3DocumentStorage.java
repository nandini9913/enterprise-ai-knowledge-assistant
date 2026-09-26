package com.nandini.knowledgeassistant.document.storage;

import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Stores documents in a private S3 bucket with server-side encryption. The bucket is never
 * exposed publicly; downloads go through the API so authorization is always enforced.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3DocumentStorage implements DocumentStorage {

    private final S3Client s3;
    private final String bucket;

    public S3DocumentStorage(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.bucket = properties.s3Bucket();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(request -> request.bucket(bucket).key(key).contentType(contentType)
                            .serverSideEncryption(ServerSideEncryption.AES256),
                    RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE, "Document storage is unavailable.", ex);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(request -> request.bucket(bucket).key(key)).asByteArray();
        } catch (SdkException ex) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE, "Document storage is unavailable.", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(request -> request.bucket(bucket).key(key));
        } catch (SdkException ex) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE, "Document storage is unavailable.", ex);
        }
    }
}
