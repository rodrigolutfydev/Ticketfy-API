package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.infra.exception.EmailAlreadyExistsException;
import com.lutfy.ticketfy.infra.security.LoginResponseDTO;
import com.lutfy.ticketfy.infra.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public User register(UserRegistrationDTO data) {
        var existingUser = repository.findByEmail(data.email());
        if (existingUser.isPresent()) {
            throw new EmailAlreadyExistsException("This email is already registered");
        }
        var encodedPassword = passwordEncoder.encode(data.password());
        var user = new User(data, Role.USER, encodedPassword);
        return repository.save(user);
    }

    @Transactional
    public LoginResponseDTO becomeOrganizer(User user) {
        user.promoteToOrganizer();
        User saved = repository.save(user);
        return new LoginResponseDTO(tokenService.generateToken(saved));
    }
}