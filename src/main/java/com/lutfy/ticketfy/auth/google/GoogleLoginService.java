package com.lutfy.ticketfy.auth.google;

import com.lutfy.ticketfy.audit.AuditAction;
import com.lutfy.ticketfy.audit.AuditService;
import com.lutfy.ticketfy.audit.AuditTargetType;
import com.lutfy.ticketfy.auth.AuthSessionService;
import com.lutfy.ticketfy.auth.IssuedSession;
import com.lutfy.ticketfy.auth.RevocationReason;
import com.lutfy.ticketfy.auth.google.GoogleIdTokenVerifier.GoogleIdentity;
import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

// Login com Google (authorization code + PKCE): o front manda o código, a API
// troca com o Google, valida o id_token e abre a mesma sessão do login com senha.
@Service
public class GoogleLoginService {

    private static final int NAME_MAX = 100;
    private static final int EMAIL_MAX = 150;

    private final GoogleSettings settings;
    private final GoogleTokenClient tokenClient;
    private final GoogleIdTokenVerifier verifier;
    private final UserRepository users;
    private final AuthSessionService authSessions;
    private final AuditService auditService;
    private final TransactionTemplate transaction;

    public GoogleLoginService(GoogleSettings settings, GoogleTokenClient tokenClient, GoogleIdTokenVerifier verifier,
                              UserRepository users, AuthSessionService authSessions, AuditService auditService,
                              PlatformTransactionManager transactionManager) {
        this.settings = settings;
        this.tokenClient = tokenClient;
        this.verifier = verifier;
        this.users = users;
        this.authSessions = authSessions;
        this.auditService = auditService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public IssuedSession login(GoogleLoginDTO dto) {
        if (!settings.enabled()) {
            throw new ProblemException(ProblemType.GOOGLE_LOGIN_UNAVAILABLE, "Google login is not available");
        }
        if (!settings.isAllowedRedirectUri(dto.redirectUri())) {
            throw new ProblemException(ProblemType.GOOGLE_REDIRECT_URI_NOT_ALLOWED, "This redirect URI is not allowed");
        }
        // Chamadas de rede fora da transação: o banco não fica preso esperando o Google.
        var idToken = tokenClient.exchange(dto.code(), dto.codeVerifier(), dto.redirectUri());
        var identity = verifier.verify(idToken);
        if (identity.email().length() > EMAIL_MAX) {
            throw new ProblemException(ProblemType.EMAIL_NOT_ALLOWED, "This email address cannot be used");
        }
        try {
            return transaction.execute(status -> signIn(identity));
        } catch (DataIntegrityViolationException ex) {
            // Outro login com a mesma conta Google criou ou ligou o usuário ao
            // mesmo tempo (uk_users_google_subject ou e-mail único): busca de novo.
            return transaction.execute(status -> signIn(identity));
        }
    }

    private IssuedSession signIn(GoogleIdentity identity) {
        var user = users.findByGoogleSubject(identity.subject())
                .orElseGet(() -> linkOrCreate(identity));
        // Conta suspensa: start() lança o mesmo account-suspended do login com
        // senha, e a transação desfaz a ligação feita acima.
        return authSessions.start(user);
    }

    private User linkOrCreate(GoogleIdentity identity) {
        var existing = findByEmail(identity.email());
        if (existing == null) {
            var created = users.save(User.fromGoogle(name(identity), identity.email(), identity.subject()));
            users.flush();
            return created;
        }
        var user = users.findForUpdate(existing.getId()).orElseThrow();
        if (user.isLinkedToGoogle()) {
            throw new ProblemException(ProblemType.GOOGLE_ACCOUNT_CONFLICT,
                    "This email is already linked to another Google account");
        }
        // O cadastro com senha não confirma o e-mail: a senha pode ter sido
        // criada por outra pessoa antes do dono entrar pelo Google. Quem acabou
        // de provar ao Google que é dono do e-mail fica com a conta: a senha é
        // apagada e as sessões abertas caem. O dono define uma senha nova pelo
        // PATCH /users/me/password (sem a senha atual), se quiser.
        boolean hadPassword = user.hasPassword();
        user.linkGoogle(identity.subject());
        user.removePassword();
        users.flush();
        int revoked = authSessions.revokeAll(user.getId(), RevocationReason.GOOGLE_LINKED);
        // "credentialRemoved", e não "passwordRemoved": a auditoria nunca leva a
        // palavra password (o AuditIntegrationTest confere isso em todas as linhas).
        auditService.record(AuditAction.GOOGLE_ACCOUNT_LINKED, AuditTargetType.USER, user.getId(),
                Map.of("revokedSessions", revoked, "credentialRemoved", hadPassword));
        return user;
    }

    // O e-mail do cadastro com senha é guardado como foi digitado: compara sem
    // diferenciar maiúsculas. Se houver mais de uma conta assim, usa a de e-mail
    // idêntico; sem ela, não dá para saber qual ligar.
    private User findByEmail(String email) {
        var matches = users.findAllByEmailIgnoreCase(email).stream()
                .filter(user -> !user.isDeleted())
                .toList();
        if (matches.size() <= 1) {
            return matches.isEmpty() ? null : matches.get(0);
        }
        return matches.stream()
                .filter(user -> user.getEmail().equals(email))
                .findFirst()
                .orElseThrow(() -> new ProblemException(ProblemType.GOOGLE_ACCOUNT_CONFLICT,
                        "More than one account uses this email. Sign in with your password."));
    }

    // Nome do perfil do Google; sem ele, a parte do e-mail antes do @.
    private static String name(GoogleIdentity identity) {
        var name = identity.name() == null ? "" : identity.name().trim();
        if (name.isEmpty()) {
            int at = identity.email().indexOf('@');
            name = at > 0 ? identity.email().substring(0, at) : identity.email();
        }
        return name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
    }
}
