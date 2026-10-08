package com.lutfy.ticketfy.payout.account;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class PixKeys {

    private static final int MAX_EMAIL_LENGTH = 77;
    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s()\\-]");
    private static final Pattern PHONE = Pattern.compile("\\+55[1-9][0-9](9\\d{8}|[2-5]\\d{7})");
    private static final Pattern RANDOM = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private PixKeys() {
    }

    public static Optional<String> normalize(PixKeyType type, String value) {
        if (type == null || value == null) return Optional.empty();
        var trimmed = value.trim();
        return switch (type) {
            case CPF -> document(DocumentType.CPF, trimmed);
            case CNPJ -> document(DocumentType.CNPJ, trimmed);
            case EMAIL -> {
                var email = trimmed.toLowerCase(Locale.ROOT);
                yield email.length() <= MAX_EMAIL_LENGTH && EMAIL.matcher(email).matches()
                        ? Optional.of(email) : Optional.empty();
            }
            case PHONE -> {
                var phone = PHONE_SEPARATORS.matcher(trimmed).replaceAll("");
                yield PHONE.matcher(phone).matches() ? Optional.of(phone) : Optional.empty();
            }
            case RANDOM -> {
                var key = trimmed.toLowerCase(Locale.ROOT);
                yield RANDOM.matcher(key).matches() ? Optional.of(key) : Optional.empty();
            }
        };
    }

    private static Optional<String> document(DocumentType type, String value) {
        return BrazilianDocuments.isValid(type, value)
                ? Optional.of(BrazilianDocuments.normalize(value))
                : Optional.empty();
    }
}
