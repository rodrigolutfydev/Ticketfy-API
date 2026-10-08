package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.auth.RefreshTokenCookie;
import com.lutfy.ticketfy.infra.security.LoginResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;
    private final RefreshTokenCookie cookie;

    public UserController(UserService service, RefreshTokenCookie cookie) {
        this.service = service;
        this.cookie = cookie;
    }

    @PostMapping
    public ResponseEntity<UserDetailsDTO> register(@RequestBody @Valid UserRegistrationDTO dto) {
        var user = service.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserDetailsDTO(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDetailsDTO> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new UserDetailsDTO(user));
    }

    @PatchMapping("/me/avatar")
    public ResponseEntity<UserDetailsDTO> updateAvatar(@AuthenticationPrincipal User user,
                                                       @RequestBody @Valid UserAvatarUpdateDTO dto) {
        return ResponseEntity.ok(service.updateAvatar(user.getId(), dto));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<LoginResponseDTO> changePassword(@AuthenticationPrincipal User user,
                                                           @RequestBody @Valid PasswordChangeDTO dto) {
        var session = service.changePassword(user.getId(), dto);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.issue(session))
                .body(new LoginResponseDTO(session));
    }

    @PostMapping("/me/organizer")
    public ResponseEntity<Void> becomeOrganizer(@AuthenticationPrincipal User user) {
        service.becomeOrganizer(user);
        return ResponseEntity.noContent().build();
    }
}