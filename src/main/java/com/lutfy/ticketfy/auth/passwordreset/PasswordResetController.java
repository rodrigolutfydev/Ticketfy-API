package com.lutfy.ticketfy.auth.passwordreset;

import com.lutfy.ticketfy.infra.security.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Em /auth para passar pelo mesmo proxy do front que o login (Origin e
// X-Ticketfy-Auth são exigidos pelo AuthOriginFilter).
@RestController
@RequestMapping("/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService service;
    private final PasswordResetLimiter limiter;
    private final ClientAddressResolver clientAddress;

    public PasswordResetController(PasswordResetService service, PasswordResetLimiter limiter,
                                   ClientAddressResolver clientAddress) {
        this.service = service;
        this.limiter = limiter;
        this.clientAddress = clientAddress;
    }

    /** Sempre 202, exista a conta ou não. */
    @PostMapping("/request")
    public ResponseEntity<Void> request(@RequestBody @Valid PasswordResetRequestDTO dto, HttpServletRequest request) {
        limiter.acquireRequest(clientAddress.resolve(request), dto.email());
        service.request(dto.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestBody @Valid PasswordResetConfirmDTO dto, HttpServletRequest request) {
        limiter.acquireConfirmation(clientAddress.resolve(request));
        service.confirm(dto.token(), dto.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
