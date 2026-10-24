package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.CustomUserDetailsService;
import jsd.toiyeujava.mini_leetcode.auth.security.JwtTokenProvider;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityUnitTest {

    @Mock
    private UserRepository userRepository;

    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(secret, 1800000, 604800000);

    @Test
    @DisplayName("JwtTokenProvider: generates, validates, and extracts claims correctly")
    void jwtTokenProvider_claimsAndValidation() {
        User user = User.builder()
                .id(42L)
                .username("coder42")
                .email("coder42@test.com")
                .role(Role.INSTRUCTOR)
                .status(UserStatus.ACTIVE)
                .build();

        String token = jwtTokenProvider.generateAccessToken(user);
        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("coder42");
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo("INSTRUCTOR");
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
        assertThat(jwtTokenProvider.getAccessTokenExpirationMs()).isEqualTo(1800000);
        assertThat(jwtTokenProvider.getRefreshTokenExpiry()).isNotNull();

        // Invalid tokens
        assertThat(jwtTokenProvider.validateToken("invalid.jwt.token")).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();

        // Expired token
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(secret, -1000, 604800000);
        String expiredToken = shortLivedProvider.generateAccessToken(user);
        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();

        // Tampered token
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1) + "badSignature";
        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();

        // Hash token
        String hash1 = JwtTokenProvider.hashToken("my-secret-token");
        String hash2 = JwtTokenProvider.hashToken("my-secret-token");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex string length

        assertThatThrownBy(() -> JwtTokenProvider.hashToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token cannot be null");
    }

    @Test
    @DisplayName("CustomUserDetailsService: loads user or throws UsernameNotFoundException")
    void customUserDetailsService_loadsUserOrThrows() {
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        User user = User.builder()
                .id(1L)
                .username("john")
                .email("john@example.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByUsernameOrEmail("john", "john")).thenReturn(Optional.of(user));
        when(userRepository.findByUsernameOrEmail("unknown", "unknown")).thenReturn(Optional.empty());

        UserDetails details = service.loadUserByUsername("john");
        assertThat(details.getUsername()).isEqualTo("john");
        assertThat(details.getPassword()).isEqualTo("hashed");

        assertThatThrownBy(() -> service.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found with username or email: unknown");
    }

    @Test
    @DisplayName("UserPrincipal: tests UserDetails methods and authorities")
    void userPrincipal_methods() {
        User user = User.builder()
                .id(99L)
                .username("admin")
                .email("admin@test.com")
                .passwordHash("adminpass")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getId()).isEqualTo(99L);
        assertThat(principal.getEmail()).isEqualTo("admin@test.com");
        assertThat(principal.getRole()).isEqualTo(Role.ADMIN);
        assertThat(principal.getUsername()).isEqualTo("admin");
        assertThat(principal.getPassword()).isEqualTo("adminpass");
        assertThat(principal.getAuthorities()).hasSize(1);
        assertThat(principal.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");

        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }
}
