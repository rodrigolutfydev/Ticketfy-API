package com.lutfy.ticketfy.user;

import java.util.UUID;

public record UserDetailsDTO(
        UUID id,
        String name,
        String email,
        Role role
) {
    public UserDetailsDTO(User user) {
        this(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}