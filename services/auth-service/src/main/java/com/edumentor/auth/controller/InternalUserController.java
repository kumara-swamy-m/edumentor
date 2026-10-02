package com.edumentor.auth.controller;

import com.edumentor.auth.dto.UserResponse;
import com.edumentor.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Service-to-service lookup. Authenticated by a shared key; the gateway never routes it to the outside. */
@RestController
@RequestMapping("/api/auth/internal")
@Tag(name = "Internal (service-to-service)")
public class InternalUserController {

    private final UserService userService;
    private final byte[] apiKey;

    public InternalUserController(UserService userService, @Value("${app.internal.api-key:}") String apiKey) {
        this.userService = userService;
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Look up a user by id (requires X-Internal-Api-Key)")
    public UserResponse getUser(@PathVariable Long id,
                                @RequestHeader(value = "X-Internal-Api-Key", required = false) String providedKey) {
        boolean valid = apiKey.length > 0 && providedKey != null
                && MessageDigest.isEqual(apiKey, providedKey.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw new AccessDeniedException("Invalid internal API key");
        }
        return userService.getUser(id);
    }
}