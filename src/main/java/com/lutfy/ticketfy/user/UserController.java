package com.lutfy.ticketfy.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<UserDetailsDTO> register(@RequestBody @Valid UserRegistrationDTO data) {
        var user = service.register(data);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserDetailsDTO(user));
    }
}