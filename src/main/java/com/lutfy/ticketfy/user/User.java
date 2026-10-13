package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.infra.exception.ProblemReason;
import com.lutfy.ticketfy.infra.exception.InvalidRoleChangeException;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class User implements UserDetails {

    public static final String DELETED_NAME = "Usuário excluído";
    public static final String DELETED_EMAIL_DOMAIN = "deleted.ticketfy.invalid";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Role role;
    private String name;
    private String email;
    private String password;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Convert(converter = LanguageConverter.class)
    private Language language = Language.DEFAULT;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant deletedAt;

    private Instant suspendedAt;

    private String suspensionReason;

    private UUID suspendedBy;

    public User(UserRegistrationDTO data, Role role, String encodedPassword) {
        this.role = role;
        this.name = data.name();
        this.email = data.email();
        this.password = encodedPassword;
        this.language = data.language() == null ? Language.DEFAULT
                : Language.fromCode(data.language()).orElse(Language.DEFAULT);
    }

    public void promoteToOrganizer() {
        if (this.getRole() == Role.ORGANIZER || this.getRole() == Role.ADMIN) {
            throw new InvalidRoleChangeException("This account cannot be promoted to organizer");
        }
        this.role = Role.ORGANIZER;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changeAvatar(String url) {
        this.avatarUrl = (url == null || url.isBlank()) ? null : url;
    }

    public void changeLanguage(Language language) {
        this.language = language;
    }

    public void anonymize(String encodedPassword, Instant now) {
        this.name = DELETED_NAME;
        this.email = "deleted-" + id + "@" + DELETED_EMAIL_DOMAIN;
        this.avatarUrl = null;
        this.password = encodedPassword;
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    public void suspend(String reason, UUID admin, Instant now) {
        if (isSuspended()) {
            throw new ProblemException(ProblemReason.ACCOUNT_ALREADY_SUSPENDED, "Account is already suspended");
        }
        this.suspendedAt = now;
        this.suspensionReason = reason;
        this.suspendedBy = admin;
    }

    public void reactivate() {
        if (!isSuspended()) {
            throw new ProblemException(ProblemReason.ACCOUNT_NOT_SUSPENDED, "Account is not suspended");
        }
        this.suspendedAt = null;
        this.suspensionReason = null;
        this.suspendedBy = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return deletedAt == null;
    }
}