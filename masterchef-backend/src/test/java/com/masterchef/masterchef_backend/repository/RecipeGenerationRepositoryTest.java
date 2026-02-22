package com.masterchef.masterchef_backend.repository;

import com.masterchef.masterchef_backend.models.RecipeGeneration;
import com.masterchef.masterchef_backend.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link RecipeGenerationRepository}.
 *
 * Covers all five methods — three of which use custom JPQL {@code @Query}
 * annotations that must be validated against a real database. Uses a real
 * PostgreSQL container (via {@link AbstractRepositoryTest}) so that TEXT[]
 * columns on the entity work correctly.
 */
@DisplayName("RecipeGenerationRepository")
class RecipeGenerationRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecipeGenerationRepository generationRepository;

    private User user;

    /** Convenience builder seeded with every required non-null field. */
    private RecipeGeneration.RecipeGenerationBuilder generation() {
        return RecipeGeneration.builder()
                .user(user)
                .ingredients(List.of("chicken", "garlic"))
                .prompt("Make a simple dinner")
                .modelUsed("mistral")
                .status("SUCCESS")
                .cached(false)
                .latencyMs(500L)
                .tokensUsed(100);
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("chef@test.com")
                .passwordHash("hash")
                .name("Test Chef")
                .build());
    }

    // ── findByUserIdOrderByCreatedAtDesc ─────────────────────────────────────

    @Nested
    @DisplayName("findByUserIdOrderByCreatedAtDesc()")
    class FindByUserIdOrderByCreatedAtDesc {

        @Test
        @DisplayName("returns generations belonging to the user in descending created-at order")
        void shouldReturnGenerationsInDescendingOrder() throws InterruptedException {
            // Persist two records — sleep briefly so timestamps differ
            generationRepository.save(generation().prompt("First prompt").build());
            Thread.sleep(10);
            generationRepository.save(generation().prompt("Second prompt").build());

            Page<RecipeGeneration> page = generationRepository
                    .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            // Most-recent (second) comes back first
            assertThat(page.getContent().get(0).getPrompt()).isEqualTo("Second prompt");
            assertThat(page.getContent().get(1).getPrompt()).isEqualTo("First prompt");
        }

        @Test
        @DisplayName("returns only the requesting user's generations")
        void shouldIsolateByUser() {
            User otherUser = userRepository.save(User.builder()
                    .email("other@test.com")
                    .passwordHash("hash")
                    .name("Other")
                    .build());

            generationRepository.save(generation().build());
            generationRepository.save(RecipeGeneration.builder()
                    .user(otherUser)
                    .ingredients(List.of("tofu"))
                    .prompt("Other prompt")
                    .modelUsed("mistral")
                    .status("SUCCESS")
                    .build());

            Page<RecipeGeneration> page = generationRepository
                    .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
        }
    }

    // ── findByUserIdAndCreatedAtAfter ────────────────────────────────────────

    @Nested
    @DisplayName("findByUserIdAndCreatedAtAfter()")
    class FindByUserIdAndCreatedAtAfter {

        @Test
        @DisplayName("returns only generations created after the given timestamp")
        void shouldFilterByCreatedAtAfter() throws InterruptedException {
            // Save one record, capture the cutoff, then save another
            generationRepository.save(generation().prompt("Old").build());
            Thread.sleep(20);
            LocalDateTime cutoff = LocalDateTime.now();
            Thread.sleep(10);
            generationRepository.save(generation().prompt("New").build());

            Page<RecipeGeneration> page = generationRepository
                    .findByUserIdAndCreatedAtAfter(user.getId(), cutoff, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getPrompt()).isEqualTo("New");
        }

        @Test
        @DisplayName("returns empty page when no generations fall after the cutoff")
        void shouldReturnEmptyWhenNoneAfterCutoff() {
            generationRepository.save(generation().build());

            LocalDateTime future = LocalDateTime.now().plusHours(1);

            Page<RecipeGeneration> page = generationRepository
                    .findByUserIdAndCreatedAtAfter(user.getId(), future, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
        }
    }

    // ── countByUserId ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("countByUserId()")
    class CountByUserId {

        @Test
        @DisplayName("returns the total number of generations for the user")
        void shouldReturnCorrectCount() {
            generationRepository.save(generation().build());
            generationRepository.save(generation().build());
            generationRepository.save(generation().build());

            assertThat(generationRepository.countByUserId(user.getId())).isEqualTo(3);
        }

        @Test
        @DisplayName("returns 0 when the user has no generations")
        void shouldReturnZeroForNewUser() {
            assertThat(generationRepository.countByUserId(user.getId())).isZero();
        }
    }

    // ── countCacheHitsByUserId (JPQL @Query) ─────────────────────────────────

    @Nested
    @DisplayName("countCacheHitsByUserId() — custom JPQL")
    class CountCacheHitsByUserId {

        @Test
        @DisplayName("counts only generations where cached = true")
        void shouldCountOnlyCachedGenerations() {
            generationRepository.save(generation().cached(true).build());
            generationRepository.save(generation().cached(true).build());
            generationRepository.save(generation().cached(false).build());

            long hits = generationRepository.countCacheHitsByUserId(user.getId());

            assertThat(hits).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 0 when the user has no cache hits")
        void shouldReturnZeroWhenNoCacheHits() {
            generationRepository.save(generation().cached(false).build());

            assertThat(generationRepository.countCacheHitsByUserId(user.getId())).isZero();
        }
    }

    // ── calculateAverageLatencyByUserId (JPQL @Query) ────────────────────────

    @Nested
    @DisplayName("calculateAverageLatencyByUserId() — custom JPQL")
    class CalculateAverageLatencyByUserId {

        @Test
        @DisplayName("returns the average latency across SUCCESS generations")
        void shouldAverageLatencyForSuccessOnly() {
            generationRepository.save(generation().status("SUCCESS").latencyMs(200L).build());
            generationRepository.save(generation().status("SUCCESS").latencyMs(400L).build());
            // ERROR status must be excluded from the average
            generationRepository.save(generation().status("ERROR").latencyMs(9999L).build());

            Double avg = generationRepository.calculateAverageLatencyByUserId(user.getId());

            assertThat(avg).isNotNull();
            assertThat(avg).isEqualTo(300.0);
        }

        @Test
        @DisplayName("returns null when the user has no SUCCESS generations")
        void shouldReturnNullWhenNoSuccessGenerations() {
            generationRepository.save(generation().status("ERROR").latencyMs(100L).build());

            Double avg = generationRepository.calculateAverageLatencyByUserId(user.getId());

            // SQL AVG over an empty set returns NULL → Java null
            assertThat(avg).isNull();
        }
    }

    // ── sumTokensUsedByUserId (JPQL @Query with COALESCE) ───────────────────

    @Nested
    @DisplayName("sumTokensUsedByUserId() — custom JPQL with COALESCE")
    class SumTokensUsedByUserId {

        @Test
        @DisplayName("sums tokens across all generations for the user")
        void shouldSumTokensCorrectly() {
            generationRepository.save(generation().tokensUsed(100).build());
            generationRepository.save(generation().tokensUsed(250).build());

            Long total = generationRepository.sumTokensUsedByUserId(user.getId());

            assertThat(total).isEqualTo(350L);
        }

        @Test
        @DisplayName("returns 0 (not null) when user has no generations — COALESCE guard")
        void shouldReturnZeroWhenNoGenerations() {
            Long total = generationRepository.sumTokensUsedByUserId(user.getId());

            // COALESCE(SUM(...), 0) — must be 0, never null
            assertThat(total).isNotNull().isZero();
        }
    }
}
