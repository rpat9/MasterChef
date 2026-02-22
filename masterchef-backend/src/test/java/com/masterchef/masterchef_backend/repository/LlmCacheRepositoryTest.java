package com.masterchef.masterchef_backend.repository;

import com.masterchef.masterchef_backend.models.LlmCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link LlmCacheRepository}.
 *
 * Four of the five methods use custom JPQL {@code @Query} annotations —
 * including a {@code @Modifying} delete query. All of these must be validated
 * against a real database. Uses a real PostgreSQL container via
 * {@link AbstractRepositoryTest}
 *
 * Note: {@code @DataJpaTest} wraps each test in a transaction that rolls
 * back after the test. The {@code @Modifying deleteExpiredEntries()} tests use
 * {@code @Transactional} on the repository method itself, which participates
 * in the surrounding test transaction correctly.
 */
@DisplayName("LlmCacheRepository")
class LlmCacheRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private LlmCacheRepository cacheRepository;

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);
    private static final LocalDateTime PAST = LocalDateTime.now().minusDays(1);

    /** Convenience builder for a non-expired cache entry. */
    private LlmCache.LlmCacheBuilder validEntry(String hash) {
        return LlmCache.builder()
                .inputHash(hash)
                .response("{\"content\": \"Pasta recipe\"}")
                .model("mistral")
                .tokensUsed(150)
                .expiresAt(FUTURE);
    }

    /** Convenience builder for an already-expired cache entry. */
    private LlmCache.LlmCacheBuilder expiredEntry(String hash) {
        return LlmCache.builder()
                .inputHash(hash)
                .response("{\"content\": \"Old pasta recipe\"}")
                .model("mistral")
                .tokensUsed(50)
                .expiresAt(PAST);
    }

    // ── findByInputHash ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByInputHash()")
    class FindByInputHash {

        @Test
        @DisplayName("returns the cache entry when the hash exists")
        void shouldReturnEntryWhenHashExists() {
            cacheRepository.save(validEntry("abc123").build());

            Optional<LlmCache> result = cacheRepository.findByInputHash("abc123");

            assertThat(result).isPresent();
            assertThat(result.get().getModel()).isEqualTo("mistral");
            assertThat(result.get().getTokensUsed()).isEqualTo(150);
        }

        @Test
        @DisplayName("returns empty Optional when no entry matches the hash")
        void shouldReturnEmptyWhenHashNotFound() {
            Optional<LlmCache> result = cacheRepository.findByInputHash("nonexistent-hash");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns an expired entry — expiry check is the caller's responsibility")
        void shouldReturnExpiredEntryToo() {
            cacheRepository.save(expiredEntry("expired-hash").build());

            Optional<LlmCache> result = cacheRepository.findByInputHash("expired-hash");

            // findByInputHash does NOT filter by expiry — that is intentional
            assertThat(result).isPresent();
        }
    }

    // ── existsByInputHashAndNotExpired (JPQL @Query) ─────────────────────────

    @Nested
    @DisplayName("existsByInputHashAndNotExpired() — custom JPQL")
    class ExistsByInputHashAndNotExpired {

        @Test
        @DisplayName("returns true when a valid non-expired entry exists")
        void shouldReturnTrueForValidEntry() {
            cacheRepository.save(validEntry("valid-hash").build());

            boolean exists = cacheRepository.existsByInputHashAndNotExpired(
                    "valid-hash", LocalDateTime.now());

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("returns false when the entry is expired")
        void shouldReturnFalseForExpiredEntry() {
            cacheRepository.save(expiredEntry("expired-hash").build());

            boolean exists = cacheRepository.existsByInputHashAndNotExpired(
                    "expired-hash", LocalDateTime.now());

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("returns false when no entry exists for the hash")
        void shouldReturnFalseWhenHashNotFound() {
            boolean exists = cacheRepository.existsByInputHashAndNotExpired(
                    "missing-hash", LocalDateTime.now());

            assertThat(exists).isFalse();
        }
    }

    // ── deleteExpiredEntries (@Modifying JPQL @Query) ────────────────────────

    @Nested
    @DisplayName("deleteExpiredEntries() — @Modifying custom JPQL")
    class DeleteExpiredEntries {

        @Test
        @DisplayName("deletes only expired entries and returns the deleted count")
        void shouldDeleteOnlyExpiredEntries() {
            cacheRepository.save(validEntry("keep-1").build());
            cacheRepository.save(validEntry("keep-2").build());
            cacheRepository.save(expiredEntry("delete-1").build());
            cacheRepository.save(expiredEntry("delete-2").build());
            cacheRepository.save(expiredEntry("delete-3").build());

            int deleted = cacheRepository.deleteExpiredEntries(LocalDateTime.now());

            assertThat(deleted).isEqualTo(3);
            // Valid entries must still be present
            assertThat(cacheRepository.findByInputHash("keep-1")).isPresent();
            assertThat(cacheRepository.findByInputHash("keep-2")).isPresent();
            // Expired entries must be gone
            assertThat(cacheRepository.findByInputHash("delete-1")).isEmpty();
        }

        @Test
        @DisplayName("returns 0 when there are no expired entries to delete")
        void shouldReturnZeroWhenNothingToDelete() {
            cacheRepository.save(validEntry("valid-entry").build());

            int deleted = cacheRepository.deleteExpiredEntries(LocalDateTime.now());

            assertThat(deleted).isZero();
        }

        @Test
        @DisplayName("returns 0 on an empty table without throwing")
        void shouldHandleEmptyTable() {
            int deleted = cacheRepository.deleteExpiredEntries(LocalDateTime.now());

            assertThat(deleted).isZero();
        }
    }

    // ── countValidEntries (JPQL @Query) ─────────────────────────────────────

    @Nested
    @DisplayName("countValidEntries() — custom JPQL")
    class CountValidEntries {

        @Test
        @DisplayName("counts only non-expired entries")
        void shouldCountOnlyNonExpiredEntries() {
            cacheRepository.save(validEntry("valid-1").build());
            cacheRepository.save(validEntry("valid-2").build());
            cacheRepository.save(expiredEntry("expired-1").build());

            long count = cacheRepository.countValidEntries(LocalDateTime.now());

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 0 when all entries are expired")
        void shouldReturnZeroWhenAllExpired() {
            cacheRepository.save(expiredEntry("expired-1").build());
            cacheRepository.save(expiredEntry("expired-2").build());

            long count = cacheRepository.countValidEntries(LocalDateTime.now());

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("returns 0 on an empty table")
        void shouldReturnZeroOnEmptyTable() {
            assertThat(cacheRepository.countValidEntries(LocalDateTime.now())).isZero();
        }
    }
}
