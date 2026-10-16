package com.lutfy.ticketfy.user;

import com.lutfy.ticketfy.auth.RefreshTokenCookie;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiHeaders;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.OpenApiExamples;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.infra.openapi.PublicEndpoint;
import com.lutfy.ticketfy.infra.security.LoginResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@Tag(name = ApiTags.USERS)
public class UserController {

    private final UserService service;
    private final RefreshTokenCookie cookie;

    public UserController(UserService service, RefreshTokenCookie cookie) {
        this.service = service;
        this.cookie = cookie;
    }

    @PostMapping
    @PublicEndpoint
    @Operation(summary = "Criar conta",
            description = "Cria uma conta com o papel `USER`. Não abre sessão: em seguida, use `POST /auth/login`.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            examples = @ExampleObject(name = "cadastro", summary = "Nova conta",
                    value = OpenApiExamples.REGISTER_REQUEST)))
    @ApiResponse(responseCode = "201", description = "Conta criada.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserDetailsDTO.class),
                    examples = @ExampleObject(name = "cadastro", summary = "Conta criada",
                            value = OpenApiExamples.REGISTER_RESPONSE)))
    @ProblemResponses({ProblemType.EMAIL_NOT_ALLOWED, ProblemType.EMAIL_ALREADY_EXISTS})
    public ResponseEntity<UserDetailsDTO> register(@RequestBody @Valid UserRegistrationDTO dto) {
        var user = service.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserDetailsDTO(user));
    }

    @GetMapping("/me")
    @Operation(summary = "Ver a própria conta")
    public ResponseEntity<UserDetailsDTO> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new UserDetailsDTO(user));
    }

    @PatchMapping("/me/avatar")
    @Operation(summary = "Trocar a foto", description = "URL `https` da imagem; `null` ou vazio remove a foto.")
    @ProblemResponses(ProblemType.SESSION_EXPIRED)
    public ResponseEntity<UserDetailsDTO> updateAvatar(@AuthenticationPrincipal User user,
                                                       @RequestBody @Valid UserAvatarUpdateDTO dto) {
        return ResponseEntity.ok(service.updateAvatar(user.getId(), dto));
    }

    @PatchMapping("/me/language")
    @Operation(summary = "Trocar o idioma", description = "Idioma usado pelo site, pelos e-mails e pelos CSVs.")
    @ProblemResponses(ProblemType.SESSION_EXPIRED)
    public ResponseEntity<UserDetailsDTO> updateLanguage(@AuthenticationPrincipal User user,
                                                         @RequestBody @Valid LanguageUpdateDTO dto) {
        return ResponseEntity.ok(service.updateLanguage(user.getId(), dto));
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Trocar ou definir a senha",
            description = "Quem tem senha confirma a atual em `currentPassword`; conta criada pelo Google define "
                    + "a primeira sem ela. As outras sessões são encerradas e uma nova é aberta.")
    @ApiResponse(responseCode = "200", description = "Senha trocada e nova sessão aberta.",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = ApiHeaders.SET_REFRESH_COOKIE))
    @ProblemResponses({ProblemType.SESSION_EXPIRED, ProblemType.INVALID_PASSWORD,
            ProblemType.TOO_MANY_PASSWORD_ATTEMPTS})
    public ResponseEntity<LoginResponseDTO> changePassword(@AuthenticationPrincipal User user,
                                                           @RequestBody @Valid PasswordChangeDTO dto) {
        var session = service.changePassword(user.getId(), dto);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.issue(session))
                .body(new LoginResponseDTO(session));
    }

    @PostMapping("/me/organizer")
    @Operation(summary = "Virar organizador",
            description = "Promove a conta `USER` a `ORGANIZER`. O papel novo vale na hora, com o mesmo token.")
    @ApiResponse(responseCode = "204", description = "Conta promovida.")
    @ProblemResponses({ProblemType.SESSION_EXPIRED, ProblemType.INVALID_ROLE_CHANGE})
    public ResponseEntity<Void> becomeOrganizer(@AuthenticationPrincipal User user) {
        service.becomeOrganizer(user);
        return ResponseEntity.noContent().build();
    }
}