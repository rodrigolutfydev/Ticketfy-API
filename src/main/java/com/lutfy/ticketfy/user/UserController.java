package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.infra.security.LoginResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UserDetailsDTO> register(@RequestBody @Valid UserRegistrationDTO dto) {
        var user = service.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserDetailsDTO(user));
    }

    @PostMapping("/me/organizer")
    public ResponseEntity<LoginResponseDTO> becomeOrganizer(@AuthenticationPrincipal User user) {
        var response = service.becomeOrganizer(user);
        return ResponseEntity.ok(response);
    }
}