package com.masterchef.masterchef_backend.repository;

import com.masterchef.masterchef_backend.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link UserRepository}.
 *
 * Runs against a real PostgreSQL container (via {@link AbstractRepositoryTest})
 * so the TEXT[] dietary_preferences column is handled correctly.
 * Every test method runs inside a transaction that rolls back automatically —
 * each test gets a clean slate without truncating tables manually.
 */
@DisplayName("UserRepository")
class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(User.builder()
                .email("chef@example.com")
                .passwordHash("hashed-password")
                .name("Gordon Ramsay")
                .build());
    }

    // ── findByEmail ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByEmail()")
    class FindByEmail {

        @Test
        @DisplayName("returns the user when the email exists")
        void shouldReturnUserWhenEmailExists() {
            Optional<User> result = userRepository.findByEmail("chef@example.com");

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Gordon Ramsay");
            assertThat(result.get().getEmail()).isEqualTo("chef@example.com");
        }

        @Test
        @DisplayName("returns empty Optional when the email does not exist")
        void shouldReturnEmptyWhenEmailNotFound() {
            Optional<User> result = userRepository.findByEmail("nobody@example.com");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("is case-sensitive — upper-case email does not match")
        void shouldBeCaseSensitive() {
            Optional<User> result = userRepository.findByEmail("CHEF@EXAMPLE.COM");

            assertThat(result).isEmpty();
        }
    }

    // ── existsByEmail ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByEmail()")
    class ExistsByEmail {

        @Test
        @DisplayName("returns true when the email is taken")
        void shouldReturnTrueForExistingEmail() {
            assertThat(userRepository.existsByEmail("chef@example.com")).isTrue();
        }

        @Test
        @DisplayName("returns false when the email is not registered")
        void shouldReturnFalseForUnknownEmail() {
            assertThat(userRepository.existsByEmail("ghost@example.com")).isFalse();
        }
    }

    // ── save / generated UUID ────────────────────────────────────────────────

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("persists a new user and generates a UUID primary key")
        void shouldPersistUserAndGenerateUUID() {
            User newUser = userRepository.save(User.builder()
                    .email("new@example.com")
                    .passwordHash("another-hash")
                    .name("Jamie Oliver")
                    .build());

            assertThat(newUser.getId()).isNotNull();
            assertThat(userRepository.findById(newUser.getId())).isPresent();
        }

        @Test
        @DisplayName("two different users get two different UUIDs")
        void shouldGenerateUniqueUUIDsForDifferentUsers() {
            User second = userRepository.save(User.builder()
                    .email("second@example.com")
                    .passwordHash("hash2")
                    .name("Julia Child")
                    .build());

            assertThat(savedUser.getId()).isNotEqualTo(second.getId());
        }
    }
}
