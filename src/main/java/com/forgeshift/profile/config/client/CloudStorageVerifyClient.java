package com.forgeshift.profile.config.client;

import com.forgeshift.profile.config.dto.CloudStorageVerifyResponse;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Instant;

/**
 * Verifies a GCS profile by:
 *   1. Building a Storage client from the supplied SA JSON
 *   2. Calling storage.get(bucket) to confirm the SA can see the bucket
 *   3. Listing up to 1 object as a read probe
 */
@Slf4j
@Component
public class CloudStorageVerifyClient {

    /**
     * @param serviceAccountJsonBytes raw JSON bytes (not base64)
     */
    public CloudStorageVerifyResponse verify(byte[] serviceAccountJsonBytes, String bucketName) {
        long start = System.currentTimeMillis();
        try {
            GoogleCredentials creds = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJsonBytes));

            Storage storage = StorageOptions.newBuilder()
                    .setCredentials(creds)
                    .build()
                    .getService();

            Bucket bucket = storage.get(bucketName);
            if (bucket == null) {
                return CloudStorageVerifyResponse.builder()
                        .success(false)
                        .bucket(bucketName)
                        .elapsedMs(System.currentTimeMillis() - start)
                        .errorMessage("Bucket " + bucketName + " not found or not accessible by this service account.")
                        .build();
            }

            int sample = 0;
            try {
                for (Blob ignored : storage.list(bucketName, Storage.BlobListOption.pageSize(1)).iterateAll()) {
                    sample++;
                    if (sample >= 1) break;
                }
            } catch (Exception e) {
                log.debug("List probe failed (non-fatal, may indicate read-only or restricted IAM): {}", e.getMessage());
            }

            return CloudStorageVerifyResponse.builder()
                    .success(true)
                    .bucket(bucketName)
                    .projectId(storage.getOptions().getProjectId())
                    .clientEmail(creds.toString().contains("@") ? creds.toString() : null)
                    .sampleCount(sample)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .verifiedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            return CloudStorageVerifyResponse.builder()
                    .success(false)
                    .bucket(bucketName)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
