package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.auth.RefreshTokenCookie;
import com.lutfy.ticketfy.auth.RevocationReason;
import com.lutfy.ticketfy.auth.google.GoogleLoginDTO;
import com.lutfy.ticketfy.auth.google.GoogleLoginService;
import com.lutfy.ticketfy.infra.exception.ProblemDetailFactory;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.infra.openapi.ApiHeaders;
import com.lutfy.ticketfy.infra.openapi.ApiTags;
import com.lutfy.ticketfy.infra.openapi.OpenApiExamples;
import com.lutfy.ticketfy.infra.openapi.ProblemResponses;
import com.lutfy.ticketfy.infra.openapi.PublicEndpoint;
import com.lutfy.ticketfy.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = ApiTags.AUTH)
public class AuthController {

    private final AuthenticationManager manager;
    private final AuthSessionService authSessions;
    private final RefreshTokenCookie cookie;
    private final ProblemDetailFactory problems;
    private final GoogleLoginService googleLogin;

    public AuthController(AuthenticationManager manager, AuthSessionService authSessions,
                          RefreshTokenCookie cookie, ProblemDetailFactory problems, GoogleLoginService googleLogin) {
        this.manager = manager;
        this.authSessions = authSessions;
        this.cookie = cookie;
        this.problems = problems;
        this.googleLogin = googleLogin;
    }

    @PostMapping("/login")
    @PublicEndpoint
    @Operation(summary = "Entrar com e-mail e senha",
            description = "Abre uma sessão: devolve o access token no corpo e o refresh token no cookie. "
                    + "Cinco tentativas por minuto por IP, somadas às de `/auth/google`.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            examples = @ExampleObject(name = "login", summary = "E-mail e senha",
                    value = OpenApiExamples.LOGIN_REQUEST)))
    @ApiResponse(responseCode = "200", description = "Sessão aberta.",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = ApiHeaders.SET_REFRESH_COOKIE),
            content = @Content(mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoginResponseDTO.class),
                    examples = @ExampleObject(name = "login", summary = "Sessão aberta",
                            value = OpenApiExamples.LOGIN_RESPONSE)))
    @ProblemResponses({ProblemType.INVALID_CREDENTIALS, ProblemType.ACCOUNT_SUSPENDED,
            ProblemType.TOO_MANY_LOGIN_ATTEMPTS})
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid AuthDTO dto) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(dto.email(), dto.password());
        var authentication = manager.authenticate(usernamePassword);
        var session = authSessions.start((User) authentication.getPrincipal());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.issue(session))
                .body(new LoginResponseDTO(session));
    }

    @PostMapping("/google")
    @PublicEndpoint
    @Operation(summary = "Entrar com o Google",
            description = "Troca o código de autorização do Google (fluxo com PKCE) por uma sessão do Ticketfy. "
                    + "Cria a conta no primeiro acesso. Se já existe conta com o mesmo e-mail, ela passa a usar o "
                    + "Google: a senha é apagada e as sessões abertas caem.")
    @ApiResponse(responseCode = "200", description = "Sessão aberta.",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = ApiHeaders.SET_REFRESH_COOKIE))
    @ProblemResponses({ProblemType.GOOGLE_REDIRECT_URI_NOT_ALLOWED, ProblemType.EMAIL_NOT_ALLOWED,
            ProblemType.GOOGLE_AUTHORIZATION_FAILED, ProblemType.GOOGLE_EMAIL_NOT_VERIFIED,
            ProblemType.ACCOUNT_SUSPENDED, ProblemType.GOOGLE_ACCOUNT_CONFLICT, ProblemType.TOO_MANY_LOGIN_ATTEMPTS,
            ProblemType.GOOGLE_UNAVAILABLE, ProblemType.GOOGLE_LOGIN_UNAVAILABLE})
    public ResponseEntity<LoginResponseDTO> google(@RequestBody @Valid GoogleLoginDTO dto) {
        var session = googleLogin.login(dto);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.issue(session))
                .body(new LoginResponseDTO(session));
    }

    @PostMapping("/refresh")
    @PublicEndpoint
    @Operation(summary = "Renovar a sessão",
            description = "Lê o refresh token do cookie, troca por um novo (uso único) e devolve um access token "
                    + "novo. Reusar um refresh token já trocado encerra a sessão inteira. Na recusa, o cookie "
                    + "também é apagado.")
    @Parameter(in = ParameterIn.COOKIE, name = ApiHeaders.REFRESH_COOKIE,
            description = "Refresh token gravado pelo login. O navegador envia sozinho.",
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    @ApiResponse(responseCode = "200", description = "Sessão renovada.",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = ApiHeaders.SET_REFRESH_COOKIE),
            content = @Content(mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoginResponseDTO.class)))
    @ProblemResponses({ProblemType.SESSION_EXPIRED, ProblemType.ACCOUNT_SUSPENDED})
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        try {
            return cookie.read(request)
                    .flatMap(authSessions::refresh)
                    .<ResponseEntity<?>>map(session -> ResponseEntity.ok()
                            .cacheControl(CacheControl.noStore())
                            .header(HttpHeaders.SET_COOKIE, cookie.issue(session))
                            .body(new LoginResponseDTO(session)))
                    .orElseGet(() -> refused(ProblemType.SESSION_EXPIRED, "Session expired. Sign in again.", request));
        } catch (ProblemException ex) {
            if (ex.getType() != ProblemType.ACCOUNT_SUSPENDED) {
                throw ex;
            }
            return refused(ProblemType.ACCOUNT_SUSPENDED, ex.getMessage(), request);
        }
    }

    private ResponseEntity<?> refused(ProblemType type, String detail, HttpServletRequest request) {
        var problem = problems.create(type, detail, request.getRequestURI());
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .body(problem);
    }

    @PostMapping("/logout")
    @PublicEndpoint
    @Operation(summary = "Sair desta sessão",
            description = "Encerra a sessão do refresh token do cookie, se houver, e apaga o cookie. "
                    + "Não exige access token, para funcionar mesmo com ele vencido.")
    @Parameter(in = ParameterIn.COOKIE, name = ApiHeaders.REFRESH_COOKIE,
            description = "Refresh token gravado pelo login. O navegador envia sozinho.",
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    @ApiResponse(responseCode = "204", description = "Sessão encerrada.",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = ApiHeaders.CLEAR_REFRESH_COOKIE))
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookie.read(request).ifPresent(authSessions::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Sair de todas as sessões",
            description = "Encerra todas as sessões abertas da conta, em qualquer aparelho.")
    @ApiResponse(responseCode = "204", description = "Sessões encerradas.",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = ApiHeaders.CLEAR_REFRESH_COOKIE))
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal User user) {
        authSessions.revokeAll(user.getId(), RevocationReason.LOGOUT_ALL);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.clear())
                .build();
    }
}
