package com.autotestai.service;

import java.util.Locale;

import com.autotestai.dto.auth.AuthResponse;
import com.autotestai.dto.auth.LoginRequest;
import com.autotestai.dto.auth.RegisterRequest;
import com.autotestai.dto.user.UserResponse;
import com.autotestai.entity.UserEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.UserMapper;
import com.autotestai.service.JwtTokenService.IssuedToken;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String ACTIVE = "ACTIVE";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = normalizeEmail(request.email());
        ensureUsernameAvailable(username);
        ensureEmailAvailable(email);

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(trimToNull(request.displayName()));
        user.setStatus(ACTIVE);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USER_ALREADY_EXISTS",
                    "Username or email is already registered");
        }
        UserEntity persistedUser = userMapper.findById(user.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "USER_CREATION_FAILED",
                        "Registered user could not be loaded"));
        return createAuthResponse(persistedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userMapper.findByUsername(request.username().trim())
                .orElseThrow(AuthService::invalidCredentials);
        if (!ACTIVE.equals(user.getStatus()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return createAuthResponse(user);
    }

    private AuthResponse createAuthResponse(UserEntity user) {
        IssuedToken token = jwtTokenService.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }

    private void ensureUsernameAvailable(String username) {
        if (userMapper.findByUsername(username).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username is already registered");
        }
    }

    private void ensureEmailAvailable(String email) {
        if (userMapper.findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }
}
