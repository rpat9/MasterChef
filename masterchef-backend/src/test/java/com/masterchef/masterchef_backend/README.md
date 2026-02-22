# MasterChef Backend — Test Suite

This document covers every test in the suite: what it tests, which tools it uses, and the concepts behind each decision.

---

## Test Philosophy

The tests follow the **Testing Pyramid**:

```
        ▲
       / \
      /   \         Integration Tests  — full Spring context, real database
     /─────\
    /       \        Controller Tests   — API tests
   /─────────\
  /           \      Unit Tests         — fast, isolated, no infrastructure
 ───────────────
```

The suite covers all three layers. Unit tests run in milliseconds with no infrastructure. Controller tests spin up only the web layer using `@WebMvcTest`. Repository tests use Testcontainers to run against a real PostgreSQL instance.

---

## Tools Used

| Tool | Role |
|---|---|
| **JUnit 5** | Test runner — `@Test`, `@BeforeEach`, `@Nested`, `@DisplayName` |
| **Mockito** | Creates fake ("mock") versions of dependencies |
| **AssertJ** | Fluent, readable assertions (`assertThat(...).isEqualTo(...)`) |
| **Spring Test — `ReflectionTestUtils`** | Injects `@Value` fields without a Spring context |
| **Micrometer — `SimpleMeterRegistry`** | Lightweight in-memory metrics registry for unit tests |
| **`@WebMvcTest` + `RestTestClient`** | Loads only the web layer — no DB, no real services |
| **`@WithMockUser`** | Injects a fake authenticated principal for security-protected endpoints |
| **`@DataJpaTest` + Testcontainers** | Runs repository tests against a real PostgreSQL container |

---

## Test Files

### 1. `MasterchefBackendApplicationTests.java`

**Location:** `src/test/java/com/masterchef/masterchef_backend/`

**What it does:** The default Spring Boot application context test. It boots the entire Spring application — including the database connection, JPA, security, and all beans — to verify that the application context loads without errors.

**Requires:** A running PostgreSQL database and all environment variables configured (JWT secret, database credentials, etc.). This test is intended to run in a fully configured environment such as a CI pipeline with Docker Compose or a local dev environment with containers running.

**Annotations used:**
- `@SpringBootTest` — Starts the full Spring application context for the test.

---

### 2. `service/RecipeServiceTest.java`

**What it tests:** `RecipeService` — the core business logic class responsible for orchestrating recipe generation. It coordinates the user lookup, ingredient normalisation, LLM call, audit logging, JSON parsing, and database persistence.

**Dependencies mocked:**
- `LlmOrchestrator` — so no real LLM calls are made
- `RecipeRepository` — so no real database writes happen
- `RecipeGenerationRepository` — same
- `UserRepository` — same

**Test groups (`@Nested` classes):**

#### `GenerateRecipeHappyPath`
Tests the normal, successful flow where all dependencies behave correctly.

| Test | What it proves |
|---|---|
| `shouldReturnRecipeResponseWithCorrectData` | The returned `RecipeResponse` carries the correct title, description, and times parsed from the LLM JSON. |
| `shouldReturnMetadataFromLlmResponse` | The `GenerationMetaData` block (model, cached flag, token count) is populated from the `LlmResponse`. |
| `shouldNormaliseIngredientsBeforeCallingLlm` | Ingredients with extra whitespace and mixed case (`"  CHICKEN  "`) are trimmed and lowercased before the LLM is called. Uses `ArgumentCaptor` to inspect the exact `LlmRequest` that was passed to the mock. |
| `shouldDeduplicateIngredients` | Duplicate ingredients (`["chicken", "chicken", "garlic"]`) are collapsed to unique values before the LLM call. |
| `shouldSaveGenerationAuditRecord` | A `RecipeGeneration` record is always saved via `recipeGenerationRepository.save()`, once per call. |
| `shouldSaveRecipeToRepository` | The parsed `Recipe` entity is saved via `recipeRepository.save()`, once per call. |
| `shouldUseCorrectModelInLlmRequest` | The model name sent to the LLM is `"mistral"`. |
| `shouldHandleMarkdownCodeFencesInLlmResponse` | When the LLM wraps its JSON in ` ```json ... ``` ` fences, the service strips them before parsing. |

#### `GenerateRecipeErrorHandling`
Tests every branch where something goes wrong.

| Test | What it proves |
|---|---|
| `shouldThrowWhenUserNotFound` | A `RuntimeException("User not found")` is thrown when the user UUID does not exist. The LLM and repository are never touched. |
| `shouldThrowWhenLlmReturnsErrorStatus` | A `RuntimeException` is thrown when the LLM returns `status = "ERROR"`. The recipe is never saved. |
| `shouldThrowWhenLlmReturnsNullContent` | Even with `status = "SUCCESS"`, a null content field throws. |
| `shouldUseFallbackWhenLlmReturnsInvalidJson` | When the LLM returns plain prose instead of JSON, the service falls back to a `"Recipe from Ingredients"` title rather than crashing. |
| `shouldSaveAuditRecordEvenOnLlmFailure` | The `RecipeGeneration` audit record is persisted even when the LLM fails — this is important for observability. |

#### `ExportRecipeAsJson`

| Test | What it proves |
|---|---|
| `shouldSerialiseRecipeToJson` | `exportRecipeAsJson()` returns a non-blank string containing the recipe title. |
| `shouldContainRecipeId` | The serialised JSON contains the recipe's UUID. |

**Key concepts demonstrated:**
- `@ExtendWith(MockitoExtension.class)` — activates Mockito without Spring
- `@Mock` / `@InjectMocks` — dependency injection via mocks
- `when(...).thenReturn(...)` — stubbing mock return values
- `ArgumentCaptor` — capturing and asserting on arguments passed to mocks
- `verifyNoInteractions(mock)` — asserting a mock was never touched
- `assertThatThrownBy(...)` — clean exception assertion

---

### 3. `service/AuthServiceTest.java`

**What it tests:** `AuthService` — handles user registration, login, and refresh token flows including password hashing, Spring Security authentication, and JWT generation.

**Dependencies mocked:**
- `UserRepository`
- `PasswordEncoder`
- `JwtTokenProvider`
- `AuthenticationManager`
- `UserDetailsServiceImpl`

**Test groups:**

#### `register()`

| Test | What it proves |
|---|---|
| `shouldReturnAuthResponseOnSuccess` | Registration returns an `AuthResponse` with the correct email, name, both tokens, and token type. |
| `shouldHashPasswordBeforeSaving` | The raw password is passed to `passwordEncoder.encode()` and the hashed result (never the raw password) is stored in the `User` entity. Uses `ArgumentCaptor<User>` to inspect the saved object. |
| `shouldThrowWhenEmailAlreadyExists` | Throws `RuntimeException("Email already registered")` on duplicate email. `PasswordEncoder` and `JwtTokenProvider` are never called. |
| `shouldSaveExactlyOneUser` | `userRepository.save()` is called exactly once per registration. |
| `shouldPopulateExpiresIn` | The `expiresIn` field in the response comes from `jwtTokenProvider.getAccessTokenExpirationSeconds()`. |

#### `login()`

| Test | What it proves |
|---|---|
| `shouldReturnAuthResponseOnSuccess` | Successful login returns tokens with the correct email and token strings. |
| `shouldThrowWhenCredentialsAreInvalid` | `BadCredentialsException` from `AuthenticationManager` propagates through. The repository and token provider are never touched. |
| `shouldThrowWhenUserNotFoundInDbAfterAuthentication` | Guards against the race condition where authentication succeeds but the user was deleted between the auth check and the DB lookup. |
| `shouldPassCorrectCredentialsToAuthManager` | The `UsernamePasswordAuthenticationToken` passed to `AuthenticationManager` contains the exact email and password from the request. |

#### `refreshToken()`

| Test | What it proves |
|---|---|
| `shouldReturnNewAccessTokenOnValidRefreshToken` | A valid refresh token produces a new `TokenRefreshResponse` with a new access token and expiry. |
| `shouldThrowWhenRefreshTokenIsInvalid` | `validateRefreshToken()` returning `false` immediately throws. No username extraction or DB lookup happens. |
| `shouldThrowWhenUserNotFoundDuringRefresh` | Guards against a deleted user presenting a still-valid refresh token. |
| `shouldValidateRefreshTokenFirst` | Confirms the validation is the first operation — no other `JwtTokenProvider` method is called when validation fails. |

**Key concepts demonstrated:**
- Mocking interfaces (`AuthenticationManager`, `PasswordEncoder`)
- Mocking a `mock(Authentication.class)` object inline
- `verify(mock, never()).method(...)` — asserting a method was never called
- `ArgumentCaptor<UsernamePasswordAuthenticationToken>` — verifying the exact authentication token constructed

---

### 4. `service/LlmCacheServiceTest.java`

**What it tests:** `LlmCacheService` — manages SHA-256 content-addressable caching of LLM responses in PostgreSQL.

**Dependencies mocked:**
- `LlmCacheRepository`

**Special setup:** The `cacheTtlDays` field is injected via `@Value` in production. In unit tests, `ReflectionTestUtils.setField(llmCacheService, "cacheTtlDays", 7)` sets it directly without Spring.

**Test groups:**

#### `isCached()`

| Test | What it proves |
|---|---|
| `shouldReturnTrueWhenEntryExists` | Returns `true` when the repository confirms a non-expired entry exists. |
| `shouldReturnFalseWhenNoEntry` | Returns `false` on a repository miss. |
| `shouldCallRepositoryWithHashAndTimestamp` | The hash passed to the repository is a 64-character lowercase hex string (SHA-256 output). The timestamp is close to `now()`. |
| `shouldProduceSameHashForIdenticalRequests` | Two identical requests produce the same hash — the hashing is deterministic. |
| `shouldProduceDifferentHashesForDifferentPrompts` | Different prompts produce different hashes — the cache correctly differentiates inputs. |

#### `getCachedResponse()`

| Test | What it proves |
|---|---|
| `shouldReturnEmptyWhenCacheMiss` | Returns `Optional.empty()` when the repository finds nothing. |
| `shouldReturnEmptyWhenCacheExpired` | Returns `Optional.empty()` even when an entry exists but its `expiresAt` is in the past. |
| `shouldReturnLlmResponseOnCacheHit` | Returns a populated `LlmResponse` with `cached = true`, `status = "CACHE_HIT"`, and `latencyMs = 0` for a valid, non-expired entry. |

#### `cacheResponse()`

| Test | What it proves |
|---|---|
| `shouldSaveEntryWhenNotAlreadyCached` | Calls `repository.save()` when no existing entry is found. |
| `shouldNotSaveDuplicateEntry` | Does not call `save()` when an entry with that hash already exists (race condition guard). |
| `shouldSaveWithCorrectModelAndTokens` | The `LlmCache` entity saved to the repository has the correct model name, token count, and response content. |
| `shouldSetCorrectExpiryTime` | The `expiresAt` timestamp on the saved entity is `now() + 7 days` (the configured TTL). |

#### `cleanupExpiredEntries()`

| Test | What it proves |
|---|---|
| `shouldReturnDeletedCount` | Returns the integer returned by `repository.deleteExpiredEntries()`. |
| `shouldReturnZeroWhenNothingToDelete` | Returns `0` when there are no expired entries. |
| `shouldPassCurrentTimeToRepository` | The `LocalDateTime` passed to the delete query is between the timestamps taken immediately before and after the call. |

#### `getStats()`

| Test | What it proves |
|---|---|
| `shouldReturnCorrectStats` | The `CacheStats` record contains the correct `validEntries` and `totalEntries` counts from the repository. |
| `shouldComputeHitRateCorrectly` | `hitRate()` returns `0.5` when 4 valid entries exist out of 8 total. |
| `shouldReturnZeroHitRateWhenEmpty` | `hitRate()` returns `0.0` when total entries is `0` — no division-by-zero error. |

**Key concepts demonstrated:**
- `ReflectionTestUtils.setField()` — injecting `@Value` fields in unit tests
- Testing private logic through its public API (hash determinism tested via `isCached()`, never directly)
- `ArgumentCaptor<LocalDateTime>` — asserting time-sensitive arguments

---

### 5. `service/LlmOrchestratorTest.java`

**What it tests:** `LlmOrchestrator` — the central routing layer that checks the cache, calls the LLM on a miss, caches successful responses, and returns structured error objects when the LLM fails.

**Special setup:** `LlmOrchestrator` has a manual constructor (not `@RequiredArgsConstructor`) that registers Micrometer metrics. `@InjectMocks` cannot resolve this, so the object is constructed manually in `@BeforeEach`:

```java
llmOrchestrator = new LlmOrchestrator(llmClient, cacheService, new SimpleMeterRegistry());
```

`SimpleMeterRegistry` is a real, in-memory Micrometer registry — it satisfies the `MeterRegistry` dependency without needing Spring or Prometheus.

**Test groups:**

#### `generateWithCache() — cache hit`

| Test | What it proves |
|---|---|
| `shouldReturnCachedResponseWithoutCallingLlm` | When the cache has a hit, the `LlmClient` is never called. The response has `cached = true` and `status = "CACHE_HIT"`. |
| `shouldNotCallCacheResponseOnCacheHit` | `cacheService.cacheResponse()` is never called for a cache hit — there is no double-caching. |

#### `generateWithCache() — cache miss`

| Test | What it proves |
|---|---|
| `shouldCallLlmClientOnCacheMiss` | The `LlmClient.generate()` is called exactly once on a cache miss. |
| `shouldReturnLlmResponseOnSuccess` | A successful LLM response is returned with `status = "SUCCESS"` and correct content. |
| `shouldCacheSuccessfulResponse` | `cacheService.cacheResponse()` is called once after a successful LLM generation. |
| `shouldNotCacheErrorResponse` | A response with `status = "ERROR"` is never passed to `cacheService.cacheResponse()` — errors must not poison the cache. |
| `shouldReturnErrorResponseWhenLlmThrows` | When the `LlmClient` throws a `RuntimeException`, the orchestrator catches it and returns a structured error response (`status = "ERROR"`) rather than propagating the exception. |

#### `utility methods`

| Test | What it proves |
|---|---|
| `isAvailable()` | Delegates to `llmClient.isAvailable()` and returns `true` when available. |
| `isAvailable() — unavailable` | Returns `false` when the client reports unavailable. |
| `getModelName()` | Delegates to `llmClient.getModelName()`. |
| `getCacheStats()` | Delegates to `cacheService.getStats()` and returns the correct `CacheStats` record. |
| `cleanupCache()` | Delegates to `cacheService.cleanupExpiredEntries()` and returns the deleted count. |

**Key concepts demonstrated:**
- Manual construction when `@InjectMocks` cannot resolve a complex constructor
- `SimpleMeterRegistry` as a zero-overhead test double for Micrometer
- `verifyNoInteractions(llmClient)` — confirming the LLM was not called on a cache hit
- `verify(cacheService, never()).cacheResponse(...)` — confirming errors are never cached

---

### 6. `service/StorageServiceTest.java`

**What it tests:** `StorageService` — wraps the AWS S3 SDK to handle recipe exports, presigned URLs, and object deletion. The `S3Client` is mocked so no real AWS calls are ever made.

**Dependencies mocked:**
- `S3Client` (AWS SDK v2)

**Special setup:** `StorageService` has four `@Value` fields (`bucketName`, `awsEndpoint`, `awsRegion`, `useLocalStack`). All four are set via `ReflectionTestUtils.setField()` in `@BeforeEach`.

**Test groups:**

#### `initializeBucket()`

| Test | What it proves |
|---|---|
| `shouldDoNothingWhenBucketExists` | When `headBucket()` succeeds, `createBucket()` is never called. |
| `shouldCreateBucketWhenItDoesNotExist` | When `headBucket()` throws `NoSuchBucketException`, `createBucket()` is called to create it. |
| `shouldThrowWhenS3Unavailable` | A general `S3Exception` from `headBucket()` causes a `RuntimeException("S3 bucket initialization failed")`. |

#### `uploadRecipeExport()`

| Test | What it proves |
|---|---|
| `shouldReturnCorrectS3KeyForJson` | The returned key follows the pattern `exports/{userId}/{recipeId}.json`. |
| `shouldReturnPdfKeyForPdfContentType` | `application/pdf` content type produces a key ending in `.pdf`. |
| `shouldSendPutRequestToCorrectBucket` | The `PutObjectRequest` targets the configured bucket name with the correct content type. Uses `ArgumentCaptor<PutObjectRequest>` to inspect the exact SDK call. |
| `shouldThrowWhenUploadFails` | An `S3Exception` from `putObject()` is wrapped in `RuntimeException("Failed to upload to S3")`. |

#### `deleteObject()`

| Test | What it proves |
|---|---|
| `shouldDeleteWithCorrectKey` | The `DeleteObjectRequest` contains the correct key and bucket name. |
| `shouldThrowWhenDeleteFails` | An `S3Exception` from `deleteObject()` is wrapped in `RuntimeException("Failed to delete object from S3")`. |

#### `isAvailable()`

| Test | What it proves |
|---|---|
| `shouldReturnTrueWhenS3IsReachable` | Returns `true` when `headBucket()` succeeds. |
| `shouldReturnFalseWhenS3IsUnreachable` | Returns `false` when `headBucket()` throws an `S3Exception` — no exception propagates to the caller. |

**Key concepts demonstrated:**
- Mocking AWS SDK clients — the SDK client is just an interface to Mockito
- Using typed matchers (`any(CreateBucketRequest.class)`) to resolve overloaded SDK methods
- `ArgumentCaptor<PutObjectRequest>` — verifying the exact SDK request object constructed
- `@PostConstruct` and unit tests — `initializeBucket()` does not auto-run in unit tests (no Spring), so it is called manually in tests that need to verify its behaviour

---

## Repository Tests

Repository tests use `@DataJpaTest` with Testcontainers. A single static PostgreSQL 16 container starts once and is shared across all repository test classes. Flyway runs the real production migration inside the container, so every test operates against the exact same schema as production.

**Infrastructure shared by all repository tests:** `AbstractRepositoryTest.java`
- Declares the static `PostgreSQLContainer` with `@Container`
- Uses `@DynamicPropertySource` to wire the container's JDBC URL, username, and password into Spring's datasource config
- Annotated with `@DataJpaTest`, `@Testcontainers`, and `@AutoConfigureTestDatabase(replace = NONE)`

> Repository tests require Docker to be running. Testcontainers will pull `postgres:16-alpine` on first run.

---

### 7. `repository/UserRepositoryTest.java`

**What it tests:** `UserRepository` — email lookup and existence checks.

| Test | What it proves |
|---|---|
| `findByEmail` returns user when found | A saved user is retrieved by exact email match. |
| `findByEmail` returns empty when not found | An unknown email returns `Optional.empty()`. |
| `existsByEmail` returns true for existing email | Confirmed existence check for a saved user. |
| `existsByEmail` returns false for unknown email | Returns `false` without throwing. |

**Key concepts demonstrated:**
- `@DataJpaTest` — loads only JPA repositories, entities, and Flyway; no web layer
- `TestEntityManager` — persists test data directly without going through the service layer

---

### 8. `repository/RecipeRepositoryTest.java`

**What it tests:** `RecipeRepository` — user-scoped queries, pagination, saved-recipe filtering, and counts.

| Test | What it proves |
|---|---|
| `findByUserId` returns only that user's recipes | Cross-user isolation — user B's recipes are never returned for user A. |
| `findByUserIdAndIsSavedTrue` returns only saved recipes | Filtering by `isSaved = true` works correctly. |
| `findByUserIdAndIsSaved(false)` returns unsaved recipes | Filtering by `isSaved = false` works correctly. |
| `findByUserId` with pagination respects page size | Page size is honoured; total elements is correct. |
| `countByUserIdAndIsSavedTrue` returns correct count | Aggregate count matches the number of saved recipes. |

**Key concepts demonstrated:**
- Paginated repository methods (`Pageable`)
- Cross-user data isolation verified with two distinct users in the same test

---

### 9. `repository/RecipeGenerationRepositoryTest.java`

**What it tests:** `RecipeGenerationRepository` — time-windowed queries, ordering, and three custom `@Query` JPQL methods.

| Test | What it proves |
|---|---|
| `findByUserIdOrderByCreatedAtDesc` returns in descending order | Most recent generation appears first. |
| `findByUserIdAndCreatedAtAfter` filters by time window | Only generations after the cutoff are returned. |
| `countByUserId` returns total count | Simple count matches number of saved records. |
| `countCacheHitsByUserId` counts only cached generations | The `WHERE cached = true` JPQL filter works correctly. |
| `calculateAverageLatencyByUserId` averages SUCCESS records only | The `WHERE status = 'SUCCESS'` filter is applied before averaging. |
| `sumTokensUsedByUserId` returns 0 when no records exist | The `COALESCE(SUM(...), 0)` guard prevents null on empty result. |

**Key concepts demonstrated:**
- Custom `@Query` JPQL tested against a real database — confirms the query syntax is valid
- `COALESCE` null-safety proven with an empty dataset

---

### 10. `repository/LlmCacheRepositoryTest.java`

**What it tests:** `LlmCacheRepository` — hash-based lookup, expiry-aware queries, bulk delete, and count.

| Test | What it proves |
|---|---|
| `findByInputHash` returns entry when hash matches | Exact hash lookup returns the correct entity. |
| `findByInputHash` returns empty when no match | Unknown hash returns `Optional.empty()`. |
| `existsByInputHashAndNotExpired` returns true for valid entry | A non-expired entry is correctly identified as valid. |
| `existsByInputHashAndNotExpired` returns false for expired entry | An entry with `expiresAt` in the past is treated as a miss. |
| `deleteExpiredEntries` removes only expired entries | Non-expired entries survive; expired entries are deleted. Returns the count of deleted rows. |
| `countValidEntries` counts only non-expired entries | Expired entries are excluded from the count. |

**Key concepts demonstrated:**
- `@Modifying @Transactional @Query DELETE` — bulk delete tested end-to-end
- Time-sensitive expiry logic verified with controlled `expiresAt` values

---

## Controller Tests

Controller tests use `@WebMvcTest` — the Spring Boot 4 slice that loads only the web layer (DispatcherServlet, filters, security, controllers). No database and no real service logic runs. All dependencies are replaced with `@MockitoBean` stubs.

Requests are made with `RestTestClient`, the new fluent HTTP test client introduced in Spring Boot 4. It replaces `MockMvc` for web layer tests.

Protected endpoints are tested using `@WithMockUser` from Spring Security Test, which injects a fake authenticated principal without requiring a real JWT token.

---

### 11. `controller/AuthControllerTest.java`

**What it tests:** `AuthController` — register, login, and token refresh endpoints. These are the only public (unauthenticated) endpoints in the API.

**Dependencies mocked:** `AuthService`, `JwtAuthenticationFilter`, `JwtTokenProvider`, `UserDetailsServiceImpl`

| Group | Test | What it proves |
|---|---|---|
| `Register` | `shouldReturn201WithAuthResponseOnSuccess` | Returns 201 Created with all auth response fields populated. |
| `Register` | `shouldReturn400WhenNameIsBlank` | `@NotBlank` on name rejects empty string. |
| `Register` | `shouldReturn400WhenEmailIsInvalid` | `@Email` rejects non-email format. |
| `Register` | `shouldReturn400WhenPasswordIsTooShort` | `@Size(min=8)` rejects short passwords. |
| `Register` | `shouldReturn400WhenBodyIsMissing` | Missing request body returns 400. |
| `Login` | `shouldReturn200WithTokensOnSuccess` | Returns 200 with both tokens and expiry. |
| `Login` | `shouldReturn400WhenEmailIsBlank` | `@NotBlank` on email rejects empty string. |
| `Login` | `shouldReturn400WhenPasswordIsBlank` | `@NotBlank` on password rejects empty string. |
| `Login` | `shouldReturn401WhenCredentialsAreWrong` | `BadCredentialsException` from service maps to 401. |
| `RefreshToken` | `shouldReturn200WithNewAccessToken` | Returns 200 with new access token and expiry. |
| `RefreshToken` | `shouldReturn400WhenRefreshTokenIsBlank` | `@NotBlank` on refresh token rejects empty string. |

**Key concepts demonstrated:**
- `@WebMvcTest` — only the web layer loads; no database, no service logic
- `RestTestClient` — Spring Boot 4's fluent HTTP test client
- `@MockitoBean` — replaces real service beans with Mockito stubs
- Auth endpoints are publicly accessible — no `@WithMockUser` needed

---

### 12. `controller/RecipeControllerTest.java`

**What it tests:** `RecipeController` — all recipe endpoints. Every endpoint requires authentication.

**Dependencies mocked:** `RecipeService`, `StorageService`, `RecipeRepository`, `RecipeGenerationRepository`, `UserRepository`

| Group | Test | What it proves |
|---|---|---|
| `GenerateRecipe` | `shouldReturn200WithRecipeResponseOnSuccess` | Returns 200 with recipe fields from the service response. |
| `GenerateRecipe` | `shouldReturn401WhenNotAuthenticated` | Unauthenticated request is rejected with 401. |
| `GenerateRecipe` | `shouldReturn400WhenTooFewIngredients` | `@Size(min=3)` on ingredients rejects lists shorter than 3. |
| `GenerateRecipe` | `shouldReturn400WhenIngredientsIsNull` | `@NotNull` rejects a missing ingredients field. |
| `GetUserRecipes` | `shouldReturn200WithPageOfRecipes` | Returns 200 with a page containing the user's recipes. |
| `GetUserRecipes` | `shouldReturn401WhenNotAuthenticated` | Unauthenticated request is rejected. |
| `GetRecipeById` | `shouldReturn200WithRecipe` | Returns 200 with the correct recipe by ID. |
| `GetRecipeById` | `shouldReturn401WhenNotAuthenticated` | Unauthenticated request is rejected. |
| `DeleteRecipe` | `shouldReturn204OnSuccess` | Returns 204 No Content; S3 failure is swallowed and does not affect the response. |
| `DeleteRecipe` | `shouldReturn401WhenNotAuthenticated` | Unauthenticated request is rejected. |

**Key concepts demonstrated:**
- `@WithMockUser` — injects a fake authenticated principal for protected endpoints
- `UserRepository` stub maps the email principal to a UUID for the controller's user lookup
- S3 error handling — `StorageService` throwing does not bubble up to the caller

---

### 13. `controller/AdminControllerTest.java`

**What it tests:** `AdminController` — cache stats and cache clear endpoints.

**Dependencies mocked:** `LlmCacheService`, `JwtAuthenticationFilter`, `JwtTokenProvider`, `UserDetailsServiceImpl`

| Group | Test | What it proves |
|---|---|---|
| `GetCacheStats` | `shouldReturn200WithCacheStats` | Returns all stat fields with correct calculated values (active entries, hit rate). |
| `GetCacheStats` | `shouldReturnZeroHitRateWhenNoRequests` | Hit rate is 0.0 when total requests is 0 — no division-by-zero. |
| `GetCacheStats` | `shouldReturn401WhenNotAuthenticated` | Unauthenticated request is rejected. |
| `ClearExpiredCache` | `shouldReturn204OnSuccess` | Returns 204 No Content after delegating to the service. |
| `ClearExpiredCache` | `shouldReturn401WhenNotAuthenticated` | Unauthenticated request is rejected. |

**Key concepts demonstrated:**
- Verifying computed fields (`activeEntries = total - expired`, `hitRate = hits / total`) through the HTTP response
- Admin endpoints are authenticated but not role-restricted in the current `SecurityConfig`

---

## Running the Tests

```bash
# Run only the service unit tests (no infrastructure required)
./mvnw test -Dtest="RecipeServiceTest,AuthServiceTest,LlmCacheServiceTest,LlmOrchestratorTest,StorageServiceTest"

# Run only the controller tests (no infrastructure required)
./mvnw test -Dtest="AuthControllerTest,RecipeControllerTest,AdminControllerTest"

# Run repository tests (requires Docker — Testcontainers spins up PostgreSQL automatically)
./mvnw test -Dtest="UserRepositoryTest,RecipeRepositoryTest,RecipeGenerationRepositoryTest,LlmCacheRepositoryTest"

# Run everything
./mvnw test
```

> **Note:** `MasterchefBackendApplicationTests` and the repository tests require Docker to be running. All other tests run with no infrastructure.

---

## Test Count Summary

| File | Layer | Tests |
|---|---|---|
| `MasterchefBackendApplicationTests` | Integration | 1 |
| `RecipeServiceTest` | Service | 17 |
| `AuthServiceTest` | Service | 13 |
| `LlmCacheServiceTest` | Service | 17 |
| `LlmOrchestratorTest` | Service | 11 |
| `StorageServiceTest` | Service | 11 |
| `UserRepositoryTest` | Repository | — |
| `RecipeRepositoryTest` | Repository | — |
| `RecipeGenerationRepositoryTest` | Repository | — |
| `LlmCacheRepositoryTest` | Repository | — |
| `AuthControllerTest` | Controller | 12 |
| `RecipeControllerTest` | Controller | 10 |
| `AdminControllerTest` | Controller | 6 |
| **Total** | | **98+** |
