package com.autotestai.security;

import com.autotestai.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUserId {

    private CurrentUserId() {
    }

    public static long from(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN_SUBJECT", "Bearer token subject is invalid");
        }
    }
}
