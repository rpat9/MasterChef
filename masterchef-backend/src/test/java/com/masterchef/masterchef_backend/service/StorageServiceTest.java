package com.masterchef.masterchef_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                   UNIT TEST — StorageService                            ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  StorageService wraps the AWS S3Client SDK. We MOCK the S3Client so     ║
 * ║  no real AWS calls are made. This is a perfect example of mocking an    ║
 * ║  external dependency — the SDK is just another interface to Mockito.    ║
 * ║                                                                          ║
 * ║  NEW CONCEPT — Mocking SDK objects that are not interfaces               ║
 * ║  S3Client is not an interface in the traditional sense, but the AWS SDK  ║
 * ║  v2 generates proxy implementations Mockito can mock just fine.          ║
 * ║                                                                          ║
 * ║  NOTE on initializeBucket():                                             ║
 * ║  initializeBucket() is annotated @PostConstruct. It runs automatically   ║
 * ║  when Spring creates the bean, but NOT during unit tests (no Spring).    ║
 * ║  We call it manually in tests that need the bucket to be "ready",        ║
 * ║  or we skip it and only test the individual methods directly.            ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StorageService — Unit Tests")
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private StorageService storageService;

    private UUID userId;
    private UUID recipeId;

    @BeforeEach
    void setUp() {
        userId   = UUID.fromString("00000000-0000-0000-0000-000000000010");
        recipeId = UUID.fromString("00000000-0000-0000-0000-000000000020");

        // Inject @Value fields manually via ReflectionTestUtils
        ReflectionTestUtils.setField(storageService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(storageService, "awsEndpoint", "http://localhost:4566");
        ReflectionTestUtils.setField(storageService, "awsRegion", "us-east-1");
        ReflectionTestUtils.setField(storageService, "useLocalStack", true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 1: initializeBucket()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("initializeBucket()")
    class InitializeBucket {

        @Test
        @DisplayName("should do nothing when the bucket already exists")
        void shouldDoNothingWhenBucketExists() {
            // headBucket succeeds (bucket exists) → no CreateBucketRequest should be sent
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());

            storageService.initializeBucket();

            // headBucket was called once, createBucket was never called
            verify(s3Client, times(1)).headBucket(any(HeadBucketRequest.class));
            verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
        }

        @Test
        @DisplayName("should create the bucket when it does not exist")
        void shouldCreateBucketWhenItDoesNotExist() {
            // headBucket throws NoSuchBucketException → service should create the bucket
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(NoSuchBucketException.builder().build());

            storageService.initializeBucket();

            verify(s3Client, times(1)).createBucket(any(CreateBucketRequest.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when S3 is completely unavailable during init")
        void shouldThrowWhenS3Unavailable() {
            // S3Exception is the general AWS SDK exception for S3 errors
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(S3Exception.builder().message("Connection refused").statusCode(503).build());

            assertThatThrownBy(() -> storageService.initializeBucket())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("S3 bucket initialization failed");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 2: uploadRecipeExport()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("uploadRecipeExport()")
    class UploadRecipeExport {

        @Test
        @DisplayName("should return the correct S3 key in the format exports/{userId}/{recipeId}.json")
        void shouldReturnCorrectS3KeyForJson() {
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            byte[] content = "{\"title\":\"Garlic Chicken\"}".getBytes();
            String key = storageService.uploadRecipeExport(userId, recipeId, content, "application/json");

            assertThat(key).isEqualTo(
                    "exports/" + userId + "/" + recipeId + ".json"
            );
        }

        @Test
        @DisplayName("should return a .pdf key when content type is application/pdf")
        void shouldReturnPdfKeyForPdfContentType() {
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            byte[] content = new byte[]{1, 2, 3}; // fake PDF bytes
            String key = storageService.uploadRecipeExport(userId, recipeId, content, "application/pdf");

            assertThat(key).endsWith(".pdf");
        }

        @Test
        @DisplayName("should send the PutObjectRequest to the correct bucket with the correct key")
        void shouldSendPutRequestToCorrectBucket() {
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            byte[] content = "{}".getBytes();
            storageService.uploadRecipeExport(userId, recipeId, content, "application/json");

            // Capture the actual PutObjectRequest sent to the S3 mock
            ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

            PutObjectRequest captured = requestCaptor.getValue();
            assertThat(captured.bucket()).isEqualTo("test-bucket");
            assertThat(captured.contentType()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("should throw RuntimeException when S3 upload fails")
        void shouldThrowWhenUploadFails() {
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(S3Exception.builder().message("Upload failed").statusCode(500).build());

            byte[] content = "{}".getBytes();

            assertThatThrownBy(() ->
                    storageService.uploadRecipeExport(userId, recipeId, content, "application/json"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to upload to S3");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 3: deleteObject()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteObject()")
    class DeleteObject {

        @Test
        @DisplayName("should call S3Client.deleteObject with the correct key")
        void shouldDeleteWithCorrectKey() {
            String key = "exports/user-id/recipe-id.json";

            storageService.deleteObject(key);

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());

            assertThat(captor.getValue().key()).isEqualTo(key);
            assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        }

        @Test
        @DisplayName("should throw RuntimeException when S3 delete fails")
        void shouldThrowWhenDeleteFails() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(S3Exception.builder().message("Access denied").statusCode(403).build());

            assertThatThrownBy(() -> storageService.deleteObject("some/key"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to delete object from S3");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 4: isAvailable()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailable {

        @Test
        @DisplayName("should return true when S3 headBucket succeeds")
        void shouldReturnTrueWhenS3IsReachable() {
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());

            boolean available = storageService.isAvailable();

            assertThat(available).isTrue();
        }

        @Test
        @DisplayName("should return false when S3 throws an S3Exception")
        void shouldReturnFalseWhenS3IsUnreachable() {
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(S3Exception.builder().message("No connection").statusCode(503).build());

            boolean available = storageService.isAvailable();

            assertThat(available).isFalse();
        }
    }
}
