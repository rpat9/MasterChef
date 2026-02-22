package com.masterchef.masterchef_backend.service;

import com.masterchef.masterchef_backend.dto.LlmRequest;
import com.masterchef.masterchef_backend.dto.LlmResponse;
import com.masterchef.masterchef_backend.models.LlmCache;
import com.masterchef.masterchef_backend.repository.LlmCacheRepository;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                  UNIT TEST — LlmCacheService                            ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  NEW CONCEPT — ReflectionTestUtils                                       ║
 * ║  LlmCacheService has a field injected by @Value ("cacheTtlDays").        ║
 * ║  In production Spring reads this from application.yml. In a unit test    ║
 * ║  there is no Spring context, so the field stays at its default (0).      ║
 * ║  ReflectionTestUtils.setField() lets us set private fields by name,      ║
 * ║  bypassing the need for a full Spring context.                            ║
 * ║                                                                          ║
 * ║  NEW CONCEPT — Testing deterministic private methods via public API      ║
 * ║  computeHash() and normalizeInput() are private. We do NOT test them     ║
 * ║  directly. Instead we call the public methods that USE them and assert   ║
 * ║  the observable outcomes. This is correct unit testing practice.         ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmCacheService — Unit Tests")
class LlmCacheServiceTest {

    @Mock
    private LlmCacheRepository cacheRepository;

    @InjectMocks
    private LlmCacheService llmCacheService;

    // Standard LLM request used across multiple tests
    private LlmRequest standardRequest;

    @BeforeEach
    void setUp() {
        // Inject the @Value field manually — no Spring context needed
        ReflectionTestUtils.setField(llmCacheService, "cacheTtlDays", 7);

        standardRequest = LlmRequest.builder()
                .prompt("Make a recipe with chicken and garlic")
                .model("mistral")
                .temperature(0.7)
                .userId("user-123")
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 1: isCached()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isCached()")
    class IsCached {

        @Test
        @DisplayName("should return true when a valid non-expired entry exists")
        void shouldReturnTrueWhenEntryExists() {
            when(cacheRepository.existsByInputHashAndNotExpired(anyString(), any(LocalDateTime.class)))
                    .thenReturn(true);

            boolean result = llmCacheService.isCached(standardRequest);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when no cache entry exists")
        void shouldReturnFalseWhenNoEntry() {
            when(cacheRepository.existsByInputHashAndNotExpired(anyString(), any(LocalDateTime.class)))
                    .thenReturn(false);

            boolean result = llmCacheService.isCached(standardRequest);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should call the repository with a non-null hash and a current timestamp")
        void shouldCallRepositoryWithHashAndTimestamp() {
            when(cacheRepository.existsByInputHashAndNotExpired(anyString(), any(LocalDateTime.class)))
                    .thenReturn(false);

            llmCacheService.isCached(standardRequest);

            // Capture the arguments that were passed to the repository
            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(cacheRepository).existsByInputHashAndNotExpired(hashCaptor.capture(), timeCaptor.capture());

            // The hash must be a 64-character hex string (SHA-256 output)
            assertThat(hashCaptor.getValue())
                    .isNotBlank()
                    .hasSize(64)
                    .matches("[a-f0-9]+"); // valid hex characters only

            // The timestamp must be close to now (within a few seconds)
            assertThat(timeCaptor.getValue()).isBefore(LocalDateTime.now().plusSeconds(5));
        }

        @Test
        @DisplayName("should produce the same hash for identical requests (determinism)")
        void shouldProduceSameHashForIdenticalRequests() {
            when(cacheRepository.existsByInputHashAndNotExpired(anyString(), any(LocalDateTime.class)))
                    .thenReturn(false);

            // Call isCached twice with the same request
            llmCacheService.isCached(standardRequest);
            llmCacheService.isCached(standardRequest);

            // Capture both hash arguments
            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            verify(cacheRepository, times(2))
                    .existsByInputHashAndNotExpired(hashCaptor.capture(), any());

            // Both calls must produce the identical hash
            assertThat(hashCaptor.getAllValues().get(0))
                    .isEqualTo(hashCaptor.getAllValues().get(1));
        }

        @Test
        @DisplayName("should produce different hashes for different prompts")
        void shouldProduceDifferentHashesForDifferentPrompts() {
            LlmRequest differentRequest = LlmRequest.builder()
                    .prompt("Make a pasta dish")  // different prompt
                    .model("mistral")
                    .temperature(0.7)
                    .build();

            when(cacheRepository.existsByInputHashAndNotExpired(anyString(), any(LocalDateTime.class)))
                    .thenReturn(false);

            llmCacheService.isCached(standardRequest);
            llmCacheService.isCached(differentRequest);

            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            verify(cacheRepository, times(2))
                    .existsByInputHashAndNotExpired(hashCaptor.capture(), any());

            // Different prompts must produce different hashes — this is fundamental to caching
            assertThat(hashCaptor.getAllValues().get(0))
                    .isNotEqualTo(hashCaptor.getAllValues().get(1));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 2: getCachedResponse()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getCachedResponse()")
    class GetCachedResponse {

        @Test
        @DisplayName("should return empty Optional when cache entry does not exist")
        void shouldReturnEmptyWhenCacheMiss() {
            when(cacheRepository.findByInputHash(anyString())).thenReturn(Optional.empty());

            Optional<LlmResponse> result = llmCacheService.getCachedResponse(standardRequest);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty Optional when cache entry is expired")
        void shouldReturnEmptyWhenCacheExpired() {
            // Build an entry that expired 1 day ago
            LlmCache expiredEntry = LlmCache.builder()
                    .id(UUID.randomUUID())
                    .inputHash("somehash")
                    .response("{\"title\": \"Old Recipe\"}")
                    .model("mistral")
                    .tokensUsed(100)
                    .expiresAt(LocalDateTime.now().minusDays(1)) // EXPIRED
                    .build();

            when(cacheRepository.findByInputHash(anyString())).thenReturn(Optional.of(expiredEntry));

            Optional<LlmResponse> result = llmCacheService.getCachedResponse(standardRequest);

            // Even though an entry was found, it is expired, so we should get empty
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return a populated LlmResponse when cache entry is valid")
        void shouldReturnLlmResponseOnCacheHit() {
            LlmCache validEntry = LlmCache.builder()
                    .id(UUID.randomUUID())
                    .inputHash("somehash")
                    .response("{\"title\": \"Garlic Chicken\"}")
                    .model("mistral")
                    .tokensUsed(250)
                    // createdAt is normally set by Hibernate's @CreationTimestamp.
                    // In a unit test there is no Hibernate, so we must set it manually.
                    // The service uses this field in a Duration.between() log statement.
                    .createdAt(LocalDateTime.now().minusHours(1))
                    .expiresAt(LocalDateTime.now().plusDays(6)) // Not expired
                    .build();

            when(cacheRepository.findByInputHash(anyString())).thenReturn(Optional.of(validEntry));

            Optional<LlmResponse> result = llmCacheService.getCachedResponse(standardRequest);

            assertThat(result).isPresent();

            LlmResponse cachedResponse = result.get();
            assertThat(cachedResponse.getContent()).isEqualTo("{\"title\": \"Garlic Chicken\"}");
            assertThat(cachedResponse.getModel()).isEqualTo("mistral");
            assertThat(cachedResponse.getTokensUsed()).isEqualTo(250);
            assertThat(cachedResponse.isCached()).isTrue(); // must be marked as cached
            assertThat(cachedResponse.getStatus()).isEqualTo("CACHE_HIT");
            assertThat(cachedResponse.getLatencyMs()).isEqualTo(0L); // cache hits are instant
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 3: cacheResponse()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cacheResponse()")
    class CacheResponse {

        private LlmResponse llmResponse;

        @BeforeEach
        void setUpResponse() {
            llmResponse = LlmResponse.builder()
                    .content("{\"title\": \"New Recipe\"}")
                    .model("mistral")
                    .tokensUsed(300)
                    .status("SUCCESS")
                    .build();
        }

        @Test
        @DisplayName("should save a new LlmCache entry when the hash is not already cached")
        void shouldSaveEntryWhenNotAlreadyCached() {
            // No existing entry for this hash
            when(cacheRepository.findByInputHash(anyString())).thenReturn(Optional.empty());

            llmCacheService.cacheResponse(standardRequest, llmResponse);

            // Verify the repository was called to save
            verify(cacheRepository, times(1)).save(any(LlmCache.class));
        }

        @Test
        @DisplayName("should NOT save a duplicate entry when hash already exists in cache")
        void shouldNotSaveDuplicateEntry() {
            // Simulate a race condition: another thread already cached this entry
            LlmCache existingEntry = LlmCache.builder()
                    .id(UUID.randomUUID())
                    .inputHash("existinghash")
                    .response("{\"title\": \"Old Recipe\"}")
                    .model("mistral")
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();

            when(cacheRepository.findByInputHash(anyString())).thenReturn(Optional.of(existingEntry));

            llmCacheService.cacheResponse(standardRequest, llmResponse);

            // save() must NOT be called — we don't overwrite existing entries
            verify(cacheRepository, never()).save(any(LlmCache.class));
        }

        @Test
        @DisplayName("should save with the correct model and token count from the LLM response")
        void shouldSaveWithCorrectModelAndTokens() {
            when(cacheRepository.findByInputHash(anyString())).thenReturn(Optional.empty());

            llmCacheService.cacheResponse(standardRequest, llmResponse);

            ArgumentCaptor<LlmCache> cacheCaptor = ArgumentCaptor.forClass(LlmCache.class);
            verify(cacheRepository).save(cacheCaptor.capture());

            LlmCache saved = cacheCaptor.getValue();
            assertThat(saved.getModel()).isEqualTo("mistral");
            assertThat(saved.getTokensUsed()).isEqualTo(300);
            assertThat(saved.getResponse()).isEqualTo("{\"title\": \"New Recipe\"}");
        }

        @Test
        @DisplayName("should set expiry to cacheTtlDays (7) days in the future")
        void shouldSetCorrectExpiryTime() {
            when(cacheRepository.findByInputHash(anyString())).thenReturn(Optional.empty());

            LocalDateTime beforeCall = LocalDateTime.now();
            llmCacheService.cacheResponse(standardRequest, llmResponse);
            LocalDateTime afterCall = LocalDateTime.now();

            ArgumentCaptor<LlmCache> cacheCaptor = ArgumentCaptor.forClass(LlmCache.class);
            verify(cacheRepository).save(cacheCaptor.capture());

            LocalDateTime expiry = cacheCaptor.getValue().getExpiresAt();

            // The expiry should be between (now + 7 days) with a small tolerance for test speed
            assertThat(expiry).isAfter(beforeCall.plusDays(7).minusSeconds(5));
            assertThat(expiry).isBefore(afterCall.plusDays(7).plusSeconds(5));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 4: cleanupExpiredEntries()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cleanupExpiredEntries()")
    class CleanupExpiredEntries {

        @Test
        @DisplayName("should return the number of deleted entries from the repository")
        void shouldReturnDeletedCount() {
            when(cacheRepository.deleteExpiredEntries(any(LocalDateTime.class))).thenReturn(5);

            int deleted = llmCacheService.cleanupExpiredEntries();

            assertThat(deleted).isEqualTo(5);
        }

        @Test
        @DisplayName("should return zero when there are no expired entries")
        void shouldReturnZeroWhenNothingToDelete() {
            when(cacheRepository.deleteExpiredEntries(any(LocalDateTime.class))).thenReturn(0);

            int deleted = llmCacheService.cleanupExpiredEntries();

            assertThat(deleted).isZero();
        }

        @Test
        @DisplayName("should pass the current time to the repository delete query")
        void shouldPassCurrentTimeToRepository() {
            when(cacheRepository.deleteExpiredEntries(any(LocalDateTime.class))).thenReturn(0);

            LocalDateTime beforeCall = LocalDateTime.now();
            llmCacheService.cleanupExpiredEntries();
            LocalDateTime afterCall = LocalDateTime.now();

            ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(cacheRepository).deleteExpiredEntries(timeCaptor.capture());

            LocalDateTime passedTime = timeCaptor.getValue();
            assertThat(passedTime).isAfterOrEqualTo(beforeCall);
            assertThat(passedTime).isBeforeOrEqualTo(afterCall);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 5: getStats()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getStats()")
    class GetStats {

        @Test
        @DisplayName("should return stats with correct valid and total counts")
        void shouldReturnCorrectStats() {
            when(cacheRepository.countValidEntries(any(LocalDateTime.class))).thenReturn(8L);
            when(cacheRepository.count()).thenReturn(10L);

            LlmCacheService.CacheStats stats = llmCacheService.getStats();

            assertThat(stats.validEntries()).isEqualTo(8L);
            assertThat(stats.totalEntries()).isEqualTo(10L);
        }

        @Test
        @DisplayName("should compute hit rate correctly from valid vs total entries")
        void shouldComputeHitRateCorrectly() {
            when(cacheRepository.countValidEntries(any(LocalDateTime.class))).thenReturn(4L);
            when(cacheRepository.count()).thenReturn(8L);

            LlmCacheService.CacheStats stats = llmCacheService.getStats();

            // 4 valid / 8 total = 0.5 (50% hit rate)
            assertThat(stats.hitRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("should return 0.0 hit rate when there are no cache entries at all")
        void shouldReturnZeroHitRateWhenEmpty() {
            when(cacheRepository.countValidEntries(any(LocalDateTime.class))).thenReturn(0L);
            when(cacheRepository.count()).thenReturn(0L);

            LlmCacheService.CacheStats stats = llmCacheService.getStats();

            // Avoid division by zero — the record's hitRate() method handles this
            assertThat(stats.hitRate()).isEqualTo(0.0);
        }
    }
}
