package com.autotestai.service;

import java.util.Locale;

import com.autotestai.dto.user.UpdateProfileRequest;
import com.autotestai.dto.user.UserResponse;
import com.autotestai.entity.UserEntity;
import com.autotestai.exception.ApiException;
import com.autotestai.mapper.UserMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String ACTIVE = "ACTIVE";

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(long userId) {
        return UserResponse.from(requireActiveUser(userId));
    }

    @Transactional
    public UserResponse updateCurrentUser(long userId, UpdateProfileRequest request) {
        UserEntity existing = requireActiveUser(userId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        userMapper.findByEmail(email)
                .filter(user -> !user.getId().equals(existing.getId()))
                .ifPresent(user -> {
                    throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
                });

        try {
            userMapper.updateProfile(userId, email, trimToNull(request.displayName()));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }
        return UserResponse.from(requireActiveUser(userId));
    }

    private UserEntity requireActiveUser(long userId) {
        UserEntity user = userMapper.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "USER_NOT_AVAILABLE",
                        "Authenticated user is no longer available"));
        if (!ACTIVE.equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_DISABLED", "User account is disabled");
        }
        return user;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
