package com.masterchef.masterchef_backend.service;

import com.masterchef.masterchef_backend.dto.LlmRequest;
import com.masterchef.masterchef_backend.dto.LlmResponse;
import com.masterchef.masterchef_backend.dto.RecipeRequest;
import com.masterchef.masterchef_backend.dto.RecipeResponse;
import com.masterchef.masterchef_backend.models.Recipe;
import com.masterchef.masterchef_backend.models.RecipeGeneration;
import com.masterchef.masterchef_backend.models.User;
import com.masterchef.masterchef_backend.repository.RecipeGenerationRepository;
import com.masterchef.masterchef_backend.repository.RecipeRepository;
import com.masterchef.masterchef_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    UNIT TEST — RecipeService                            ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  WHAT IS A UNIT TEST?                                                    ║
 * ║  A unit test tests ONE class in complete isolation. Every collaborator   ║
 * ║  (repository, orchestrator, etc.) is replaced with a "mock" — a fake    ║
 * ║  object that we fully control. No Spring context, no database, no        ║
 * ║  network. Just pure Java logic.                                          ║
 * ║                                                                          ║
 * ║  WHY @ExtendWith(MockitoExtension.class)?                                ║
 * ║  This tells JUnit 5 to activate Mockito. Mockito will:                  ║
 * ║    1. Find all @Mock fields and create fake implementations              ║
 * ║    2. Find the @InjectMocks field and inject those fakes into it         ║
 * ║    3. Reset all mocks between each test (so tests don't interfere)       ║
 * ║                                                                          ║
 * ║  KEY ANNOTATIONS USED HERE:                                              ║
 * ║  @Mock          → Creates a fake object (we control its behaviour)       ║
 * ║  @InjectMocks   → Creates the REAL object and injects the @Mocks into it ║
 * ║  @BeforeEach    → Runs before every single @Test method                  ║
 * ║  @Nested        → Groups related tests for readability                   ║
 * ║  @DisplayName   → Human-readable test names in IDE / CI reports          ║
 * ║                                                                          ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecipeService — Unit Tests")
class RecipeServiceTest {

    // ── Mocks ──────────────────────────────────────────────────────────────────
    // These are FAKE objects. Mockito generates them at runtime.
    // When code calls recipeRepository.save(...), nothing actually hits a database —
    // Mockito intercepts the call and returns whatever we configured with when(...).

    @Mock
    private LlmOrchestrator llmOrchestrator;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeGenerationRepository recipeGenerationRepository;

    @Mock
    private UserRepository userRepository;

    // ── System Under Test ─────────────────────────────────────────────────────
    // This is the REAL RecipeService with all its @Mock dependencies injected.
    // @InjectMocks uses constructor injection (matching @RequiredArgsConstructor).
    // NOTE: ObjectMapper inside RecipeService is created with `new ObjectMapper()`
    // directly (not injected), so it is a real instance — which is fine for unit tests.

    @InjectMocks
    private RecipeService recipeService;

    // ── Shared Test Data ───────────────────────────────────────────────────────
    // Defined once here, reused across tests. This avoids copy-paste and makes
    // tests easier to read. @BeforeEach re-initialises them before every test.

    private UUID userId;
    private User testUser;
    private RecipeRequest validRequest;
    private LlmResponse successfulLlmResponse;
    private Recipe savedRecipe;
    private RecipeGeneration savedGeneration;

    @BeforeEach
    void setUp() {
        // A fixed UUID so we can assert against it in tests
        userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        testUser = User.builder()
                .id(userId)
                .name("Test User")
                .email("test@example.com")
                .passwordHash("hashed")
                .build();

        // A valid recipe request — 3 ingredients is the minimum per @Size constraint
        validRequest = RecipeRequest.builder()
                .ingredients(List.of("chicken", "garlic", "olive oil"))
                .servings(4)
                .difficulty("easy")
                .build();

        // This is what we'll tell the mock LlmOrchestrator to return
        // It contains a JSON string that RecipeService will parse into a Recipe entity
        successfulLlmResponse = LlmResponse.builder()
                .content("""
                        {
                          "title": "Garlic Chicken",
                          "description": "A simple garlic chicken dish",
                          "prepTime": 10,
                          "cookTime": 25,
                          "difficulty": "easy",
                          "cuisine": "Mediterranean",
                          "instructions": ["Season chicken", "Fry with garlic", "Serve"],
                          "ingredients": [{"name": "chicken", "amount": "500", "unit": "g"}],
                          "nutritionInfo": {"calories": 350, "protein": 30, "carbs": 5, "fat": 20},
                          "tags": ["Quick", "Healthy"]
                        }
                        """)
                .model("mistral")
                .tokensUsed(250)
                .cached(false)
                .status("SUCCESS")
                .build();

        // What the repository returns after saving
        savedRecipe = Recipe.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Garlic Chicken")
                .description("A simple garlic chicken dish")
                .prepTime(10)
                .cookTime(25)
                .totalTime(35)
                .servings(4)
                .difficulty("easy")
                .cuisine("Mediterranean")
                .ingredientsUsed(List.of("chicken", "garlic", "olive oil"))
                .instructions("[\"Season chicken\",\"Fry with garlic\",\"Serve\"]")
                .ingredients("[{\"name\":\"chicken\",\"amount\":\"500\",\"unit\":\"g\"}]")
                .nutritionInfo("{\"calories\":350,\"protein\":30,\"carbs\":5,\"fat\":20}")
                .tags(List.of("Quick", "Healthy"))
                .isSaved(true)
                .build();

        savedGeneration = RecipeGeneration.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .status("SUCCESS")
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 1: generateRecipe — Happy Path
    //
    // "Happy path" = the scenario where everything works correctly.
    // We test this FIRST because it verifies the core, correct behaviour.
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateRecipe — happy path")
    class GenerateRecipeHappyPath {

        @Test
        @DisplayName("should return a RecipeResponse with correct title and metadata")
        void shouldReturnRecipeResponseWithCorrectData() {
            // ── ARRANGE ────────────────────────────────────────────────────────
            // "Arrange" is where you set up your mocks.
            // `when(X).thenReturn(Y)` means: "whenever this mock method is called
            //  with argument X, return Y instead of doing real work."

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(successfulLlmResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);
            when(recipeRepository.save(any(Recipe.class))).thenReturn(savedRecipe);

            // ── ACT ────────────────────────────────────────────────────────────
            // "Act" = call the method you are testing with real arguments.

            RecipeResponse result = recipeService.generateRecipe(validRequest, userId);

            // ── ASSERT ─────────────────────────────────────────────────────────
            // "Assert" = verify the result is exactly what you expected.
            // AssertJ's `assertThat(...).isNotNull()` reads like English, which
            // makes failures much easier to understand than JUnit's assertEquals().

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Garlic Chicken");
            assertThat(result.getDescription()).isEqualTo("A simple garlic chicken dish");
            assertThat(result.getPrepTime()).isEqualTo(10);
            assertThat(result.getCookTime()).isEqualTo(25);
            assertThat(result.getTotalTime()).isEqualTo(35);
        }

        @Test
        @DisplayName("should return metadata with model name and cached flag")
        void shouldReturnMetadataFromLlmResponse() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(successfulLlmResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);
            when(recipeRepository.save(any(Recipe.class))).thenReturn(savedRecipe);

            RecipeResponse result = recipeService.generateRecipe(validRequest, userId);

            // The metadata object inside RecipeResponse should reflect the LlmResponse
            assertThat(result.getMetadata()).isNotNull();
            assertThat(result.getMetadata().getModel()).isEqualTo("mistral");
            assertThat(result.getMetadata().getCached()).isFalse();
            assertThat(result.getMetadata().getTokensUsed()).isEqualTo(250);
        }

        @Test
        @DisplayName("should normalise ingredients before calling the LLM — trims and lowercases")
        void shouldNormaliseIngredientsBeforeCallingLlm() {
            // We give dirty input (extra spaces, uppercase) — the service should clean it
            RecipeRequest dirtyRequest = RecipeRequest.builder()
                    .ingredients(List.of("  CHICKEN  ", "  Garlic  ", "olive oil"))
                    .servings(4)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(successfulLlmResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);
            when(recipeRepository.save(any(Recipe.class))).thenReturn(savedRecipe);

            recipeService.generateRecipe(dirtyRequest, userId);

            // ArgumentCaptor lets us "capture" the actual argument passed to a mock
            // so we can inspect it in our assertion.
            // This is more powerful than any() because we can verify WHAT was passed.
            ArgumentCaptor<LlmRequest> llmRequestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
            verify(llmOrchestrator).generateWithCache(llmRequestCaptor.capture());

            LlmRequest capturedRequest = llmRequestCaptor.getValue();
            assertThat(capturedRequest.getIngredients())
                    .containsExactlyInAnyOrder("chicken", "garlic", "olive oil");
        }

        @Test
        @DisplayName("should remove duplicate ingredients before calling the LLM")
        void shouldDeduplicateIngredients() {
            RecipeRequest requestWithDuplicates = RecipeRequest.builder()
                    .ingredients(List.of("chicken", "chicken", "garlic", "garlic", "olive oil"))
                    .servings(4)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(successfulLlmResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);
            when(recipeRepository.save(any(Recipe.class))).thenReturn(savedRecipe);

            recipeService.generateRecipe(requestWithDuplicates, userId);

            ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
            verify(llmOrchestrator).generateWithCache(captor.capture());

            // After deduplication, we should have exactly 3 distinct ingredients
            assertThat(captor.getValue().getIngredients()).hasSize(3);
        }

        @Test
        @DisplayName("should save a RecipeGeneration audit record for every call")
        void shouldSaveGenerationAuditRecord() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(successfulLlmResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);
            when(recipeRepository.save(any(Recipe.class))).thenReturn(savedRecipe);

            recipeService.generateRecipe(validRequest, userId);

            // verify() checks that this mock method was called exactly once.
            // If it was never called, or called more than once, the test fails.
            verify(recipeGenerationRepository, times(1)).save(any(RecipeGeneration.class));
        }

        @Test
        @DisplayName("should save the parsed Recipe entity to the database")
        void shouldSaveRecipeToRepository() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(successfulLlmResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);
            when(recipeRepository.save(any(Recipe.class))).thenReturn(savedRecipe);

            recipeService.generateRecipe(validRequest, userId);

            verify(recipeRepository, times(1)).save(any(Recipe.class));
        }

        @Test
        @DisplayName("should send the correct model name in the LLM request")
        void shouldUseCorrectModelInLlmRequest() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(successfulLlmResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);
            when(recipeRepository.save(any(Recipe.class))).thenReturn(savedRecipe);

            recipeService.generateRecipe(validRequest, userId);

            ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
            verify(llmOrchestrator).generateWithCache(captor.capture());

            assertThat(captor.getValue().getModel()).isEqualTo("mistral");
        }

        @Test
        @DisplayName("should handle LLM JSON wrapped in markdown code fences")
        void shouldHandleMarkdownCodeFencesInLlmResponse() {
            // Some LLMs return JSON wrapped in ```json ... ``` — the service must strip these
            LlmResponse markdownWrappedResponse = LlmResponse.builder()
                    .content("""
                            ```json
                            {
                              "title": "Pasta",
                              "description": "Simple pasta",
                              "prepTime": 5,
                              "cookTime": 15,
                              "difficulty": "easy",
                              "cuisine": "Italian",
                              "instructions": ["Boil water", "Cook pasta"],
                              "ingredients": [{"name": "pasta", "amount": "200", "unit": "g"}],
                              "nutritionInfo": {"calories": 300, "protein": 10, "carbs": 60, "fat": 5},
                              "tags": ["Quick"]
                            }
                            ```""")
                    .model("mistral")
                    .tokensUsed(150)
                    .cached(false)
                    .status("SUCCESS")
                    .build();

            Recipe pastaRecipe = Recipe.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .title("Pasta")
                    .description("Simple pasta")
                    .prepTime(5)
                    .cookTime(15)
                    .totalTime(20)
                    .servings(4)
                    .isSaved(true)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(markdownWrappedResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);
            when(recipeRepository.save(any(Recipe.class))).thenReturn(pastaRecipe);

            RecipeResponse result = recipeService.generateRecipe(validRequest, userId);

            // It should NOT throw. The fallback path for bad JSON creates a recipe with a
            // default title, but the markdown-stripping path should parse correctly.
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Pasta");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 2: generateRecipe — Edge Cases & Error Paths
    //
    // Testing error paths is just as important as happy paths.
    // A service that crashes unexpectedly in production is worse than one that
    // returns a clean error message.
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateRecipe — error handling")
    class GenerateRecipeErrorHandling {

        @Test
        @DisplayName("should throw RuntimeException when user is not found")
        void shouldThrowWhenUserNotFound() {
            // userRepository returns empty — simulating a missing user in the DB
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // assertThatThrownBy is AssertJ's way to assert that a block of code
            // throws a specific exception. Much cleaner than try/catch in tests.
            assertThatThrownBy(() -> recipeService.generateRecipe(validRequest, userId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");

            // Because we threw early, the LLM should never have been called.
            // verifyNoInteractions() checks that NO methods were called on this mock.
            verifyNoInteractions(llmOrchestrator);
            verifyNoInteractions(recipeRepository);
        }

        @Test
        @DisplayName("should throw RuntimeException when LLM returns an ERROR status")
        void shouldThrowWhenLlmReturnsErrorStatus() {
            LlmResponse failedResponse = LlmResponse.builder()
                    .content(null)
                    .model("mistral")
                    .status("ERROR")
                    .errorMessage("Model unavailable")
                    .cached(false)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(failedResponse);
            // Generation audit record IS saved even on failure (important for observability)
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);

            assertThatThrownBy(() -> recipeService.generateRecipe(validRequest, userId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("LLM generation failed");

            // The recipe itself should NOT be saved to the database on failure
            verifyNoInteractions(recipeRepository);
        }

        @Test
        @DisplayName("should throw RuntimeException when LLM returns null content with SUCCESS status")
        void shouldThrowWhenLlmReturnsNullContent() {
            LlmResponse nullContentResponse = LlmResponse.builder()
                    .content(null)
                    .model("mistral")
                    .status("SUCCESS") // status says success but content is null — defensive check
                    .cached(false)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(nullContentResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);

            assertThatThrownBy(() -> recipeService.generateRecipe(validRequest, userId))
                    .isInstanceOf(RuntimeException.class);

            verifyNoInteractions(recipeRepository);
        }

        @Test
        @DisplayName("should use fallback recipe when LLM returns non-JSON content")
        void shouldUseFallbackWhenLlmReturnsInvalidJson() {
            // When the LLM returns prose instead of JSON, parseRecipeFromLlm catches the
            // JsonProcessingException and builds a fallback Recipe with default title.
            LlmResponse badJsonResponse = LlmResponse.builder()
                    .content("Here is a great recipe for chicken! Just cook it with garlic...")
                    .model("mistral")
                    .tokensUsed(100)
                    .cached(false)
                    .status("SUCCESS")
                    .build();

            Recipe fallbackRecipe = Recipe.builder()
                    .id(UUID.randomUUID())
                    .user(testUser)
                    .title("Recipe from Ingredients")
                    .description("Generated recipe (parsing failed)")
                    .isSaved(true)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(badJsonResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);
            when(recipeRepository.save(any(Recipe.class))).thenReturn(fallbackRecipe);

            // Should NOT throw — the service handles bad JSON gracefully
            RecipeResponse result = recipeService.generateRecipe(validRequest, userId);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Recipe from Ingredients");
        }

        @Test
        @DisplayName("should still save audit record even when LLM fails")
        void shouldSaveAuditRecordEvenOnLlmFailure() {
            LlmResponse failedResponse = LlmResponse.builder()
                    .content(null)
                    .model("mistral")
                    .status("ERROR")
                    .errorMessage("Timeout")
                    .cached(false)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(llmOrchestrator.generateWithCache(any(LlmRequest.class))).thenReturn(failedResponse);
            when(recipeGenerationRepository.save(any(RecipeGeneration.class))).thenReturn(savedGeneration);

            // We expect the exception to bubble up
            assertThatThrownBy(() -> recipeService.generateRecipe(validRequest, userId))
                    .isInstanceOf(RuntimeException.class);

            // But the audit record MUST have been saved before the exception was thrown
            verify(recipeGenerationRepository, times(1)).save(any(RecipeGeneration.class));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECTION 3: exportRecipeAsJson
    //
    // This is a simple public method that serialises a Recipe entity to a
    // JSON string. Testing it is straightforward.
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("exportRecipeAsJson")
    class ExportRecipeAsJson {

        @Test
        @DisplayName("should serialise a recipe entity to a non-blank JSON string")
        void shouldSerialiseRecipeToJson() {
            String json = recipeService.exportRecipeAsJson(savedRecipe);

            assertThat(json).isNotBlank();
            // A valid JSON string serialised from our recipe must contain the title
            assertThat(json).contains("Garlic Chicken");
        }

        @Test
        @DisplayName("should produce a string that contains the recipe ID")
        void shouldContainRecipeId() {
            Recipe recipe = Recipe.builder()
                    .id(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                    .user(testUser)
                    .title("Test")
                    .isSaved(true)
                    .build();

            String json = recipeService.exportRecipeAsJson(recipe);

            assertThat(json).contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        }
    }
}
