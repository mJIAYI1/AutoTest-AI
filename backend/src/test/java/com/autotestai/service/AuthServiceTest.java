package com.autotestai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.autotestai.dto.auth.AuthResponse;
import com.autotestai.dto.auth.RegisterRequest;
import com.autotestai.entity.UserEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.UserMapper;
import com.autotestai.service.JwtTokenService.IssuedToken;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerNormalizesUserAndStoresOnlyEncodedPassword() {
        RegisterRequest request = new RegisterRequest(
                " alice ",
                "ALICE@Example.com ",
                "StrongPass123!",
                " Alice ");
        when(userMapper.findByUsername("alice")).thenReturn(Optional.empty());
        when(userMapper.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("StrongPass123!")).thenReturn("{bcrypt}encoded");
        when(userMapper.insert(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(42L);
            return 1;
        });
        UserEntity persistedUser = new UserEntity();
        persistedUser.setId(42L);
        persistedUser.setUsername("alice");
        persistedUser.setEmail("alice@example.com");
        persistedUser.setPasswordHash("{bcrypt}encoded");
        persistedUser.setDisplayName("Alice");
        persistedUser.setStatus("ACTIVE");
        when(userMapper.findById(42L)).thenReturn(Optional.of(persistedUser));
        when(jwtTokenService.issue(persistedUser)).thenReturn(new IssuedToken("jwt-value", 7200));

        AuthResponse response = authService.register(request);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(userCaptor.capture());
        UserEntity inserted = userCaptor.getValue();
        assertThat(inserted.getUsername()).isEqualTo("alice");
        assertThat(inserted.getEmail()).isEqualTo("alice@example.com");
        assertThat(inserted.getDisplayName()).isEqualTo("Alice");
        assertThat(inserted.getPasswordHash()).isEqualTo("{bcrypt}encoded");
        assertThat(response.accessToken()).isEqualTo("jwt-value");
        assertThat(response.user().id()).isEqualTo(42L);
    }

    @Test
    void registerRejectsDuplicateUsernameBeforeHashingPassword() {
        RegisterRequest request = new RegisterRequest(
                "alice",
                "alice@example.com",
                "StrongPass123!",
                null);
        when(userMapper.findByUsername("alice")).thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiException.getCode()).isEqualTo("USERNAME_TAKEN");
                });
        verifyNoInteractions(passwordEncoder, jwtTokenService);
    }
}
