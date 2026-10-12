package com.lutfy.ticketfy.user.admin;

import com.lutfy.ticketfy.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AccountSuspensionService service;

    public AdminUserController(AccountSuspensionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AdminUserDTO> findByEmail(@RequestParam String email) {
        return ResponseEntity.ok(service.findByEmail(email));
    }

    @PostMapping("/{id}/suspension")
    public ResponseEntity<AdminUserDTO> suspend(@PathVariable UUID id, @RequestBody @Valid SuspensionDTO dto,
                                                @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(service.suspend(id, dto.reason(), admin));
    }

    @DeleteMapping("/{id}/suspension")
    public ResponseEntity<AdminUserDTO> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.reactivate(id));
    }
}
