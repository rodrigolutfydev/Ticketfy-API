package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.auth.RefreshTokenCookie;
import com.lutfy.ticketfy.auth.RevocationReason;
import com.lutfy.ticketfy.infra.exception.ProblemDetailFactory;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager manager;
    private final AuthSessionService authSessions;
    private final RefreshTokenCookie cookie;
    private final ProblemDetailFactory problems;

    public AuthController(AuthenticationManager manager, AuthSessionService authSessions,
                          RefreshTokenCookie cookie, ProblemDetailFactory problems) {
        this.manager = manager;
        this.authSessions = authSessions;
        this.cookie = cookie;
        this.problems = problems;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid AuthDTO dto) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(dto.email(), dto.password());
        var authentication = manager.authenticate(usernamePassword);
        var session = authSessions.start((User) authentication.getPrincipal());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.issue(session))
                .body(new LoginResponseDTO(session));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        return cookie.read(request)
                .flatMap(authSessions::refresh)
                .<ResponseEntity<?>>map(session -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .header(HttpHeaders.SET_COOKIE, cookie.issue(session))
                        .body(new LoginResponseDTO(session)))
                .orElseGet(() -> {
                    var problem = problems.create(ProblemType.SESSION_EXPIRED, "Session expired. Sign in again.",
                            request.getRequestURI());
                    return ResponseEntity.status(problem.getStatus())
                            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                            .cacheControl(CacheControl.noStore())
                            .header(HttpHeaders.SET_COOKIE, cookie.clear())
                            .body(problem);
                });
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookie.read(request).ifPresent(authSessions::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal User user) {
        authSessions.revokeAll(user.getId(), RevocationReason.LOGOUT_ALL);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .build();
    }
}
