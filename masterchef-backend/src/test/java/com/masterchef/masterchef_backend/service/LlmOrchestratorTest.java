package com.masterchef.masterchef_backend.service;

import com.masterchef.masterchef_backend.dto.LlmRequest;
import com.masterchef.masterchef_backend.dto.LlmResponse;
import com.masterchef.masterchef_backend.llm.LlmClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                 UNIT TEST — LlmOrchestrator                             ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  LlmOrchestrator does NOT use @RequiredArgsConstructor — it has a        ║
 * ║  manual constructor that accepts a MeterRegistry to register metrics.    ║
 * ║  This means we cannot use @InjectMocks directly because Mockito would    ║
 * ║  not know which constructor to call, or how to pass the MeterRegistry.  ║
 * ║                                                                          ║
 * ║  SOLUTION: Construct the object MANUALLY in @BeforeEach, passing the    ║
 * ║  mocks and a real SimpleMeterRegistry (it's lightweight, no Spring       ║
 * ║  needed). This is a perfectly valid approach for services with complex   ║
 * ║  constructors.                                                            ║
 * ║                                                                          ║
 * ║  KEY CONCEPTS DEMONSTRATED:                                              ║
 * ║  - Testing cache-hit vs cache-miss routing logic                         ║
 * ║  - Verifying that caching is called only on SUCCESS responses            ║
 * ║  - Testing fallback / error response shapes                              ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmOrchestrator — Unit Tests")
class LlmOrchestratorTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private LlmCacheService cacheService;

    // The orchestrator is constructed MANUALLY because it has a non-trivial constructor
    private LlmOrchestrator llmOrchestrator;

    private LlmRequest standardRequest;
    private LlmResponse successResponse;

    @BeforeEach
    void setUp() {
        // SimpleMeterRegistry is an in-memory registry — perfect for unit tests.
        // It satisfies the MeterRegistry dependency without any Spring or Prometheus setup.
        llmOrchestrator = new LlmOrchestrator(llmClient, cacheService, new SimpleMeterRegistry());

        standardRequest = LlmRequest.builder()
                .prompt("Make me a garlic chicken recipe")
                .model("mistral")
                .temperature(0.7)
                .userId("user-abc")
                .build();

        successResponse = LlmResponse.builder()
                .content("{\"title\": \"Garlic Chicken\"}")
                .model("mistral")
                .tokensUsed(200)
                .cached(false)
                .status("SUCCESS")
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 1: generateWithCache() — Cache Hit Path
    //
    // When the cache has a valid entry, the LlmClient must never be called.
    // This is the most critical routing test.
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateWithCache() — cache hit")
    class CacheHit {

        @Test
        @DisplayName("should return the cached response immediately without calling the LLM client")
        void shouldReturnCachedResponseWithoutCallingLlm() {
            LlmResponse cachedResponse = LlmResponse.builder()
                    .content("{\"title\": \"Cached Recipe\"}")
                    .model("mistral")
                    .tokensUsed(0)
                    .cached(true)
                    .status("CACHE_HIT")
                    .build();

            // Cache has a hit
            when(cacheService.getCachedResponse(any(LlmRequest.class)))
                    .thenReturn(Optional.of(cachedResponse));

            LlmResponse result = llmOrchestrator.generateWithCache(standardRequest);

            assertThat(result.isCached()).isTrue();
            assertThat(result.getStatus()).isEqualTo("CACHE_HIT");
            assertThat(result.getContent()).isEqualTo("{\"title\": \"Cached Recipe\"}");

            // The real LLM was never called — this saves money and time
            verifyNoInteractions(llmClient);
        }

        @Test
        @DisplayName("should NOT call cacheResponse when serving from cache (no double-caching)")
        void shouldNotCallCacheResponseOnCacheHit() {
            LlmResponse cachedResponse = LlmResponse.builder()
                    .content("{\"title\": \"Cached\"}")
                    .model("mistral")
                    .cached(true)
                    .status("CACHE_HIT")
                    .build();

            when(cacheService.getCachedResponse(any(LlmRequest.class)))
                    .thenReturn(Optional.of(cachedResponse));

            llmOrchestrator.generateWithCache(standardRequest);

            // cacheResponse() must never be called for a cache hit — that would be redundant
            verify(cacheService, never()).cacheResponse(any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 2: generateWithCache() — Cache Miss Path
    //
    // When the cache is empty, the LlmClient must be called, and the response
    // must be cached if successful.
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateWithCache() — cache miss")
    class CacheMiss {

        @BeforeEach
        void setUpCacheMiss() {
            // Cache always misses in this section
            when(cacheService.getCachedResponse(any(LlmRequest.class)))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("should call the LLM client when there is no cache hit")
        void shouldCallLlmClientOnCacheMiss() {
            when(llmClient.generate(any(LlmRequest.class))).thenReturn(successResponse);

            llmOrchestrator.generateWithCache(standardRequest);

            verify(llmClient, times(1)).generate(any(LlmRequest.class));
        }

        @Test
        @DisplayName("should return the LLM response content on a successful generation")
        void shouldReturnLlmResponseOnSuccess() {
            when(llmClient.generate(any(LlmRequest.class))).thenReturn(successResponse);

            LlmResponse result = llmOrchestrator.generateWithCache(standardRequest);

            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getContent()).isEqualTo("{\"title\": \"Garlic Chicken\"}");
        }

        @Test
        @DisplayName("should call cacheResponse after a successful LLM generation")
        void shouldCacheSuccessfulResponse() {
            when(llmClient.generate(any(LlmRequest.class))).thenReturn(successResponse);

            llmOrchestrator.generateWithCache(standardRequest);

            // cacheService.cacheResponse() must be called exactly once with the
            // original request and the LLM's response
            verify(cacheService, times(1)).cacheResponse(
                    any(LlmRequest.class),
                    any(LlmResponse.class)
            );
        }

        @Test
        @DisplayName("should NOT cache a failed LLM response")
        void shouldNotCacheErrorResponse() {
            LlmResponse errorResponse = LlmResponse.builder()
                    .model("mistral")
                    .status("ERROR")
                    .errorMessage("Timeout")
                    .cached(false)
                    .build();

            when(llmClient.generate(any(LlmRequest.class))).thenReturn(errorResponse);

            llmOrchestrator.generateWithCache(standardRequest);

            // An error response must NEVER be cached — it would poison the cache
            verify(cacheService, never()).cacheResponse(any(), any());
        }

        @Test
        @DisplayName("should return an ERROR status response when the LLM client throws an exception")
        void shouldReturnErrorResponseWhenLlmThrows() {
            // Simulate a network error or model crash
            when(llmClient.generate(any(LlmRequest.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            LlmResponse result = llmOrchestrator.generateWithCache(standardRequest);

            // The orchestrator must NOT propagate the exception — it returns a structured error
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("ERROR");
            assertThat(result.getErrorMessage()).contains("Failed to generate response after retries");
            assertThat(result.isCached()).isFalse();

            // Error responses are never cached
            verify(cacheService, never()).cacheResponse(any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 3: Utility methods — isAvailable(), getModelName(), getCacheStats()
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("utility methods")
    class UtilityMethods {

        @Test
        @DisplayName("isAvailable() should delegate to llmClient.isAvailable()")
        void shouldDelegateIsAvailableToLlmClient() {
            when(llmClient.isAvailable()).thenReturn(true);

            boolean result = llmOrchestrator.isAvailable();

            assertThat(result).isTrue();
            verify(llmClient).isAvailable();
        }

        @Test
        @DisplayName("isAvailable() should return false when LLM client reports unavailable")
        void shouldReturnFalseWhenLlmUnavailable() {
            when(llmClient.isAvailable()).thenReturn(false);

            boolean result = llmOrchestrator.isAvailable();

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("getModelName() should delegate to llmClient.getModelName()")
        void shouldDelegateGetModelNameToLlmClient() {
            when(llmClient.getModelName()).thenReturn("mistral:latest");

            String modelName = llmOrchestrator.getModelName();

            assertThat(modelName).isEqualTo("mistral:latest");
        }

        @Test
        @DisplayName("getCacheStats() should delegate to cacheService.getStats()")
        void shouldDelegateCacheStatsToCacheService() {
            LlmCacheService.CacheStats expectedStats = new LlmCacheService.CacheStats(5L, 10L);
            when(cacheService.getStats()).thenReturn(expectedStats);

            LlmCacheService.CacheStats stats = llmOrchestrator.getCacheStats();

            assertThat(stats.validEntries()).isEqualTo(5L);
            assertThat(stats.totalEntries()).isEqualTo(10L);
        }

        @Test
        @DisplayName("cleanupCache() should delegate to cacheService.cleanupExpiredEntries()")
        void shouldDelegateCleanupToCacheService() {
            when(cacheService.cleanupExpiredEntries()).thenReturn(3);

            int deleted = llmOrchestrator.cleanupCache();

            assertThat(deleted).isEqualTo(3);
            verify(cacheService).cleanupExpiredEntries();
        }
    }
}
