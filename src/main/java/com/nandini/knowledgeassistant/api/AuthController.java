package com.nandini.knowledgeassistant.api;

import com.nandini.knowledgeassistant.api.dto.AuthDtos.CurrentUserResponse;
import com.nandini.knowledgeassistant.api.dto.AuthDtos.LoginRequest;
import com.nandini.knowledgeassistant.api.dto.AuthDtos.RegisterRequest;
import com.nandini.knowledgeassistant.api.dto.AuthDtos.TokenResponse;
import com.nandini.knowledgeassistant.api.dto.AuthDtos.UserResponse;
import com.nandini.knowledgeassistant.security.AuthenticatedUser;
import com.nandini.knowledgeassistant.security.CurrentUser;
import com.nandini.knowledgeassistant.security.JwtService;
import com.nandini.knowledgeassistant.user.Role;
import com.nandini.knowledgeassistant.user.User;
import com.nandini.knowledgeassistant.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user (always with the USER role)")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(userService.register(request.username(), request.email(), request.password(),
                Role.USER));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange username and password for a JWT access token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.username(), request.password());
        JwtService.IssuedToken token = jwtService.issue(user);
        return new TokenResponse(token.token(), "Bearer", token.expiresAt(), UserResponse.from(user));
    }

    @GetMapping("/me")
    @Operation(summary = "The identity carried by the current token")
    public CurrentUserResponse me() {
        AuthenticatedUser user = CurrentUser.require();
        return new CurrentUserResponse(user.id(), user.username(), user.role());
    }
}
