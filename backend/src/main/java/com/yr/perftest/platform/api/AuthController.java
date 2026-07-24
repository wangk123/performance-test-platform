package com.yr.perftest.platform.api;

import com.yr.perftest.platform.identity.AuthTokenService;
import com.yr.perftest.platform.identity.AuthenticatedUser;
import com.yr.perftest.platform.identity.PersistentAuthenticationService;
import com.yr.perftest.platform.identity.SystemRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final PersistentAuthenticationService authenticationService;
    private final AuthTokenService authTokenService;

    public AuthController(
            PersistentAuthenticationService authenticationService,
            AuthTokenService authTokenService
    ) {
        this.authenticationService = authenticationService;
        this.authTokenService = authTokenService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser user = authenticationService.authenticate(request.username(), request.password());
        String token = authTokenService.issue(user.getUsername());
        return new LoginResponse(user.getUsername(), user.getDisplayName(), user.getRoles(), token);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            authTokenService.revoke(authorization.substring(7).trim());
        }
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record LoginResponse(
            String username,
            String displayName,
            Set<SystemRole> roles,
            String token
    ) {
    }
}
