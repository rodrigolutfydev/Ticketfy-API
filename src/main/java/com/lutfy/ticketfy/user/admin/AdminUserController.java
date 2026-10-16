package com.lutfy.ticketfy.user.admin;

import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = ApiTags.ADMIN)
public class AdminUserController {

    private final AccountSuspensionService service;

    public AdminUserController(AccountSuspensionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Buscar conta pelo e-mail")
    @ProblemResponses(ProblemType.USER_NOT_FOUND)
    public ResponseEntity<AdminUserDTO> findByEmail(@RequestParam String email) {
        return ResponseEntity.ok(service.findByEmail(email));
    }

    @PostMapping("/{id}/suspension")
    @Operation(summary = "Suspender conta",
            description = "Encerra todas as sessões, cancela os pedidos pendentes e pausa as vendas dos eventos "
                    + "da conta. Login e renovação passam a responder 403 `account-suspended`. Conta ADMIN não "
                    + "é suspensa.")
    @ProblemResponses({ProblemType.USER_NOT_FOUND, ProblemType.ADMIN_ACCOUNT_SUSPENSION,
            ProblemType.INVALID_SUSPENSION_STATE})
    public ResponseEntity<AdminUserDTO> suspend(@PathVariable UUID id, @RequestBody @Valid SuspensionDTO dto,
                                                @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(service.suspend(id, dto.reason(), admin));
    }

    @DeleteMapping("/{id}/suspension")
    @Operation(summary = "Reativar conta")
    @ProblemResponses({ProblemType.USER_NOT_FOUND, ProblemType.INVALID_SUSPENSION_STATE})
    public ResponseEntity<AdminUserDTO> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.reactivate(id));
    }
}
