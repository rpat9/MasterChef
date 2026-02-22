package com.masterchef.masterchef_backend.repository;

import com.masterchef.masterchef_backend.models.Recipe;
import com.masterchef.masterchef_backend.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link RecipeRepository}.
 *
 * Uses a real PostgreSQL container (via {@link AbstractRepositoryTest}) so that
 * JSONB and TEXT[] columns are handled correctly by Hibernate. Each test is
 * transactional and rolls back automatically.
 */
@DisplayName("RecipeRepository")
class RecipeRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    private User userA;
    private User userB;

    // Helper: minimal valid Recipe for userA
    private Recipe.RecipeBuilder recipeFor(User user) {
        return Recipe.builder()
                .user(user)
                .title("Spaghetti Carbonara")
                .instructions("{\"steps\": []}")
                .ingredients("[]")
                .ingredientsUsed(List.of("pasta", "egg"))
                .isSaved(true);
    }

    @BeforeEach
    void setUp() {
        userA = userRepository.save(User.builder()
                .email("userA@example.com")
                .passwordHash("hash-a")
                .name("User A")
                .build());
        userB = userRepository.save(User.builder()
                .email("userB@example.com")
                .passwordHash("hash-b")
                .name("User B")
                .build());
    }

    // ── findByUserIdAndIsSavedTrue ───────────────────────────────────────────

    @Nested
    @DisplayName("findByUserIdAndIsSavedTrue()")
    class FindByUserIdAndIsSavedTrue {

        @Test
        @DisplayName("returns only saved recipes belonging to the user")
        void shouldReturnOnlySavedRecipesForUser() {
            recipeRepository.save(recipeFor(userA).isSaved(true).title("Saved Recipe").build());
            recipeRepository.save(recipeFor(userA).isSaved(false).title("Unsaved Recipe").build());

            List<Recipe> result = recipeRepository.findByUserIdAndIsSavedTrue(userA.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("Saved Recipe");
        }

        @Test
        @DisplayName("does not return recipes belonging to other users")
        void shouldNotReturnOtherUsersRecipes() {
            recipeRepository.save(recipeFor(userA).isSaved(true).build());
            recipeRepository.save(recipeFor(userB).isSaved(true).title("User B Recipe").build());

            List<Recipe> result = recipeRepository.findByUserIdAndIsSavedTrue(userA.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUser().getId()).isEqualTo(userA.getId());
        }

        @Test
        @DisplayName("returns empty list when user has no saved recipes")
        void shouldReturnEmptyListWhenNoSavedRecipes() {
            recipeRepository.save(recipeFor(userA).isSaved(false).build());

            List<Recipe> result = recipeRepository.findByUserIdAndIsSavedTrue(userA.getId());

            assertThat(result).isEmpty();
        }
    }

    // ── findByUserId (paginated) ─────────────────────────────────────────────

    @Nested
    @DisplayName("findByUserId() with pagination")
    class FindByUserId {

        @Test
        @DisplayName("returns all recipes for the user across pages")
        void shouldReturnAllUserRecipes() {
            recipeRepository.save(recipeFor(userA).title("Recipe 1").build());
            recipeRepository.save(recipeFor(userA).title("Recipe 2").build());
            recipeRepository.save(recipeFor(userA).title("Recipe 3").build());

            Page<Recipe> page = recipeRepository.findByUserId(
                    userA.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getContent()).hasSize(3);
        }

        @Test
        @DisplayName("honours page size — only returns the requested number of records")
        void shouldHonourPageSize() {
            recipeRepository.save(recipeFor(userA).title("Recipe A1").build());
            recipeRepository.save(recipeFor(userA).title("Recipe A2").build());
            recipeRepository.save(recipeFor(userA).title("Recipe A3").build());

            Page<Recipe> page = recipeRepository.findByUserId(
                    userA.getId(), PageRequest.of(0, 2));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("does not bleed recipes from other users into the result")
        void shouldIsolateByUser() {
            recipeRepository.save(recipeFor(userA).title("User A Recipe").build());
            recipeRepository.save(recipeFor(userB).title("User B Recipe").build());

            Page<Recipe> page = recipeRepository.findByUserId(
                    userA.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getTitle()).isEqualTo("User A Recipe");
        }
    }

    // ── findByUserIdAndIsSaved (filtered + paginated) ────────────────────────

    @Nested
    @DisplayName("findByUserIdAndIsSaved()")
    class FindByUserIdAndIsSaved {

        @Test
        @DisplayName("returns only saved recipes when isSaved = true")
        void shouldReturnSavedRecipesWhenFilterTrue() {
            recipeRepository.save(recipeFor(userA).isSaved(true).title("Saved").build());
            recipeRepository.save(recipeFor(userA).isSaved(false).title("Unsaved").build());

            Page<Recipe> page = recipeRepository.findByUserIdAndIsSaved(
                    userA.getId(), true, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getTitle()).isEqualTo("Saved");
        }

        @Test
        @DisplayName("returns only unsaved recipes when isSaved = false")
        void shouldReturnUnsavedRecipesWhenFilterFalse() {
            recipeRepository.save(recipeFor(userA).isSaved(true).title("Saved").build());
            recipeRepository.save(recipeFor(userA).isSaved(false).title("Unsaved").build());

            Page<Recipe> page = recipeRepository.findByUserIdAndIsSaved(
                    userA.getId(), false, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getTitle()).isEqualTo("Unsaved");
        }
    }

    // ── countByUserIdAndIsSavedTrue ──────────────────────────────────────────

    @Nested
    @DisplayName("countByUserIdAndIsSavedTrue()")
    class CountByUserIdAndIsSavedTrue {

        @Test
        @DisplayName("returns the correct count of saved recipes")
        void shouldReturnCorrectSavedCount() {
            recipeRepository.save(recipeFor(userA).isSaved(true).build());
            recipeRepository.save(recipeFor(userA).isSaved(true).build());
            recipeRepository.save(recipeFor(userA).isSaved(false).build());

            long count = recipeRepository.countByUserIdAndIsSavedTrue(userA.getId());

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 0 when the user has no saved recipes")
        void shouldReturnZeroWhenNoSavedRecipes() {
            recipeRepository.save(recipeFor(userA).isSaved(false).build());

            long count = recipeRepository.countByUserIdAndIsSavedTrue(userA.getId());

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("does not count other users' saved recipes")
        void shouldNotCountOtherUsersRecipes() {
            recipeRepository.save(recipeFor(userA).isSaved(true).build());
            // userB has 5 saved — must not affect userA's count
            for (int i = 0; i < 5; i++) {
                recipeRepository.save(recipeFor(userB).isSaved(true).title("B " + i).build());
            }

            assertThat(recipeRepository.countByUserIdAndIsSavedTrue(userA.getId())).isEqualTo(1);
        }
    }
}
