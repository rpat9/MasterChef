package com.masterchef.masterchef_backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.masterchef.masterchef_backend.dto.RecipeRequest;
import com.masterchef.masterchef_backend.dto.RecipeResponse;
import com.masterchef.masterchef_backend.models.Recipe;
import com.masterchef.masterchef_backend.models.User;
import com.masterchef.masterchef_backend.repository.RecipeGenerationRepository;
import com.masterchef.masterchef_backend.repository.RecipeRepository;
import com.masterchef.masterchef_backend.repository.UserRepository;
import com.masterchef.masterchef_backend.security.JwtAuthenticationFilter;
import com.masterchef.masterchef_backend.security.JwtTokenProvider;
import com.masterchef.masterchef_backend.security.UserDetailsServiceImpl;
import com.masterchef.masterchef_backend.service.RecipeService;
import com.masterchef.masterchef_backend.service.StorageService;

/*
 * Controller layer tests for RecipeController.
 *
 * All recipe endpoints require authentication. @WithMockUser injects a fake
 * authenticated principal into the security context, satisfying the JWT filter
 * without a real token. UserRepository is stubbed to return a matching User
 * so the controller can resolve the UUID from the email principal.
 *
 * What is tested here:
 *   - 401 Unauthenticated : requests without a principal are rejected
 *   - 200/201 Happy path  : correct status code and response body fields
 *   - 400 Validation      : @Valid constraints on RecipeRequest
 *   - 204 No content      : delete operations
 *   - Routing             : correct HTTP method and URL mapping
 */
@WebMvcTest(RecipeController.class)
@AutoConfigureRestTestClient
class RecipeControllerTest {

    @Autowired
    private RestTestClient restTestClient;

    // Controller dependencies
    @MockitoBean
    private RecipeService recipeService;
    @MockitoBean
    private StorageService storageService;
    @MockitoBean
    private RecipeRepository recipeRepository;
    @MockitoBean
    private RecipeGenerationRepository recipeGenerationRepository;
    @MockitoBean
    private UserRepository userRepository;

    // Spring Security infrastructure
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    private UUID userId;
    private UUID recipeId;
    private User mockUser;
    private RecipeResponse sampleRecipeResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        recipeId = UUID.randomUUID();

        mockUser = User.builder()
                .id(userId)
                .email("chef@example.com")
                .name("Gordon")
                .passwordHash("hashed")
                .build();

        sampleRecipeResponse = RecipeResponse.builder()
                .id(recipeId)
                .title("Chicken Tikka Masala")
                .description("A rich and creamy curry")
                .prepTime(20)
                .cookTime(40)
                .totalTime(60)
                .servings(4)
                .difficulty("medium")
                .isSaved(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("chef@example.com")).thenReturn(Optional.of(mockUser));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/recipes/generate
    // -------------------------------------------------------------------------

    @Nested
    class GenerateRecipe {

        @Test
        @WithMockUser(username = "chef@example.com")
        void shouldReturn200WithRecipeResponseOnSuccess() {
            when(recipeService.generateRecipe(any(RecipeRequest.class), eq(userId)))
                    .thenReturn(sampleRecipeResponse);

            RecipeRequest request = RecipeRequest.builder()
                    .ingredients(List.of("chicken", "garlic", "tomato"))
                    .servings(4)
                    .build();

            restTestClient.post().uri("/api/v1/recipes/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.title").isEqualTo("Chicken Tikka Masala")
                    .jsonPath("$.servings").isEqualTo(4)
                    .jsonPath("$.difficulty").isEqualTo("medium");
        }

        @Test
        void shouldReturn401WhenNotAuthenticated() {
            RecipeRequest request = RecipeRequest.builder()
                    .ingredients(List.of("chicken", "garlic", "tomato"))
                    .build();

            restTestClient.post().uri("/api/v1/recipes/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @WithMockUser(username = "chef@example.com")
        void shouldReturn400WhenTooFewIngredients() {
            // @Size(min = 3) — only 2 ingredients provided
            RecipeRequest request = RecipeRequest.builder()
                    .ingredients(List.of("chicken", "garlic"))
                    .build();

            restTestClient.post().uri("/api/v1/recipes/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @WithMockUser(username = "chef@example.com")
        void shouldReturn400WhenIngredientsIsNull() {
            // @NotNull — missing ingredients list
            RecipeRequest request = RecipeRequest.builder().build();

            restTestClient.post().uri("/api/v1/recipes/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/recipes
    // -------------------------------------------------------------------------

    @Nested
    class GetUserRecipes {

        @Test
        @WithMockUser(username = "chef@example.com")
        void shouldReturn200WithPageOfRecipes() {
            Recipe recipe = Recipe.builder()
                    .id(recipeId)
                    .title("Chicken Tikka Masala")
                    .user(mockUser)
                    .isSaved(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(recipeRepository.findByUserId(eq(userId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(recipe)));

            restTestClient.get().uri("/api/v1/recipes")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].title").isEqualTo("Chicken Tikka Masala")
                    .jsonPath("$.totalElements").isEqualTo(1);
        }

        @Test
        void shouldReturn401WhenNotAuthenticated() {
            restTestClient.get().uri("/api/v1/recipes")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/recipes/{id}
    // -------------------------------------------------------------------------

    @Nested
    class GetRecipeById {

        @Test
        @WithMockUser(username = "chef@example.com")
        void shouldReturn200WithRecipe() {
            Recipe recipe = Recipe.builder()
                    .id(recipeId)
                    .title("Chicken Tikka Masala")
                    .user(mockUser)
                    .isSaved(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));

            restTestClient.get().uri("/api/v1/recipes/" + recipeId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(recipeId.toString())
                    .jsonPath("$.title").isEqualTo("Chicken Tikka Masala");
        }

        @Test
        void shouldReturn401WhenNotAuthenticated() {
            restTestClient.get().uri("/api/v1/recipes/" + UUID.randomUUID())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/recipes/{id}
    // -------------------------------------------------------------------------

    @Nested
    class DeleteRecipe {

        @Test
        @WithMockUser(username = "chef@example.com")
        void shouldReturn204OnSuccess() {
            Recipe recipe = Recipe.builder()
                    .id(recipeId)
                    .title("Chicken Tikka Masala")
                    .user(mockUser)
                    .isSaved(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
            doNothing().when(recipeRepository).delete(recipe);
            // S3 delete failing is swallowed by the controller — 204 still returned
            doThrow(new RuntimeException("S3 unavailable")).when(storageService).deleteObject(any());

            restTestClient.delete().uri("/api/v1/recipes/" + recipeId)
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        void shouldReturn401WhenNotAuthenticated() {
            restTestClient.delete().uri("/api/v1/recipes/" + UUID.randomUUID())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}
