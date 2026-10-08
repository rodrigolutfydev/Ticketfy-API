package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.auth.AuthSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

@Component
public class ClientAddressResolver {

    public static final String CLIENT_IP_HEADER = "X-Client-IP";
    public static final String PROXY_SECRET_HEADER = "X-Proxy-Secret";

    private static final Pattern ADDRESS = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final byte[] proxySecretDigest;

    public ClientAddressResolver(AuthSettings settings) {
        this.proxySecretDigest = settings.proxySecret().isEmpty() ? null : digest(settings.proxySecret());
    }

    public String resolve(HttpServletRequest request) {
        var clientIp = request.getHeader(CLIENT_IP_HEADER);
        if (clientIp != null && ADDRESS.matcher(clientIp.trim()).matches() && fromTrustedProxy(request)) {
            return clientIp.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean fromTrustedProxy(HttpServletRequest request) {
        var provided = request.getHeader(PROXY_SECRET_HEADER);
        return proxySecretDigest != null && provided != null
                && MessageDigest.isEqual(proxySecretDigest, digest(provided.trim()));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
