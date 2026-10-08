package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.auth.IssuedSession;
import com.lutfy.ticketfy.auth.RevocationReason;
import com.lutfy.ticketfy.infra.exception.EmailAlreadyExistsException;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.exception.UserNotFoundException;
import com.lutfy.ticketfy.infra.security.PasswordConfirmation;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordConfirmation passwordConfirmation;
    private final AuthSessionService authSessions;
    private final AuditService auditService;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder,
                       PasswordConfirmation passwordConfirmation, AuthSessionService authSessions,
                       AuditService auditService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.passwordConfirmation = passwordConfirmation;
        this.authSessions = authSessions;
        this.auditService = auditService;
    }

    public User register(UserRegistrationDTO data) {
        if (data.email().trim().toLowerCase(Locale.ROOT).endsWith("@" + User.DELETED_EMAIL_DOMAIN)) {
            throw new ProblemException(ProblemType.EMAIL_NOT_ALLOWED, "This email address cannot be used");
        }
        var existingUser = repository.findByEmail(data.email());
        if (existingUser.isPresent()) {
            throw new EmailAlreadyExistsException("This email is already registered");
        }
        var encodedPassword = passwordEncoder.encode(data.password());
        var user = new User(data, Role.USER, encodedPassword);
        return repository.save(user);
    }

    @Transactional
    public void becomeOrganizer(User user) {
        lockActive(user.getId()).promoteToOrganizer();
    }

    @Transactional
    public IssuedSession changePassword(UUID userId, PasswordChangeDTO dto) {
        var user = lockActive(userId);
        passwordConfirmation.verify(user, dto.currentPassword());
        user.changePassword(passwordEncoder.encode(dto.newPassword()));
        int revoked = authSessions.revokeAll(user.getId(), RevocationReason.PASSWORD_CHANGED);
        auditService.record(AuditAction.PASSWORD_CHANGED, AuditTargetType.USER, user.getId(),
                Map.of("revokedSessions", revoked));
        return authSessions.start(user);
    }

    @Transactional
    public UserDetailsDTO updateAvatar(UUID userId, UserAvatarUpdateDTO dto) {
        var user = lockActive(userId);
        user.changeAvatar(dto.avatarUrl());
        return new UserDetailsDTO(user);
    }

    private User lockActive(UUID userId) {
        var user = repository.findForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (user.isDeleted()) {
            throw UserLocks.accountDeleted();
        }
        return user;
    }
}
