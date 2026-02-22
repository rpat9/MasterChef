package com.masterchef.masterchef_backend.controller;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.masterchef.masterchef_backend.security.JwtAuthenticationFilter;
import com.masterchef.masterchef_backend.security.JwtTokenProvider;
import com.masterchef.masterchef_backend.security.UserDetailsServiceImpl;
import com.masterchef.masterchef_backend.service.LlmCacheService;

/*
 * Controller layer tests for AdminController.
 *
 * Admin endpoints sit behind authentication but have no role restriction in
 * the current SecurityConfig (anyRequest().authenticated()). @WithMockUser
 * satisfies that constraint without a real JWT.
 *
 * What is tested here:
 *   - 401 Unauthenticated : requests without a principal are rejected
 *   - 200 Happy path      : GET /cache/stats returns correct JSON shape
 *   - 204 No content      : DELETE /cache returns empty body on success
 *   - Delegation          : controller delegates to LlmCacheService correctly
 */
@WebMvcTest(AdminController.class)
@AutoConfigureRestTestClient
class AdminControllerTest {

    @Autowired
    private RestTestClient restTestClient;

    @MockitoBean
    private LlmCacheService llmCacheService;

    // Spring Security infrastructure
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    // -------------------------------------------------------------------------
    // GET /api/v1/admin/cache/stats
    // -------------------------------------------------------------------------

    @Nested
    class GetCacheStats {

        @Test
        @WithMockUser
        void shouldReturn200WithCacheStats() {
            when(llmCacheService.getTotalCacheEntries()).thenReturn(100L);
            when(llmCacheService.getExpiredCacheEntries()).thenReturn(20L);
            when(llmCacheService.getCacheHitCount()).thenReturn(60L);
            when(llmCacheService.getCacheMissCount()).thenReturn(40L);

            restTestClient.get().uri("/api/v1/admin/cache/stats")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalEntries").isEqualTo(100)
                    .jsonPath("$.expiredEntries").isEqualTo(20)
                    .jsonPath("$.activeEntries").isEqualTo(80)
                    .jsonPath("$.hitRate").isEqualTo(0.6)
                    .jsonPath("$.totalHits").isEqualTo(60)
                    .jsonPath("$.totalMisses").isEqualTo(40);
        }

        @Test
        @WithMockUser
        void shouldReturnZeroHitRateWhenNoRequests() {
            when(llmCacheService.getTotalCacheEntries()).thenReturn(0L);
            when(llmCacheService.getExpiredCacheEntries()).thenReturn(0L);
            when(llmCacheService.getCacheHitCount()).thenReturn(0L);
            when(llmCacheService.getCacheMissCount()).thenReturn(0L);

            restTestClient.get().uri("/api/v1/admin/cache/stats")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.hitRate").isEqualTo(0.0);
        }

        @Test
        void shouldReturn401WhenNotAuthenticated() {
            restTestClient.get().uri("/api/v1/admin/cache/stats")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/admin/cache
    // -------------------------------------------------------------------------

    @Nested
    class ClearExpiredCache {

        @Test
        @WithMockUser
        void shouldReturn204OnSuccess() {
            when(llmCacheService.clearExpiredCache()).thenReturn(15);

            restTestClient.delete().uri("/api/v1/admin/cache")
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        void shouldReturn401WhenNotAuthenticated() {
            restTestClient.delete().uri("/api/v1/admin/cache")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}
