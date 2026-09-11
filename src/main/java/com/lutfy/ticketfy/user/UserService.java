package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.infra.exception.EmailAlreadyExistsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(UserRegistrationDTO data) throws EmailAlreadyExistsException {
        var existingUser = repository.findByEmail(data.email());
        if (existingUser.isPresent()) {
            throw new EmailAlreadyExistsException("This email is already registered");
        }

        var encodedPassword = passwordEncoder.encode(data.password());
        var user = new User(data, Role.USER, encodedPassword);

        return repository.save(user);
    }
}