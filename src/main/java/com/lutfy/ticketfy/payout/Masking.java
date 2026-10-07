package com.lutfy.ticketfy.payout;

public final class Masking {

    private Masking() {
    }

    public static String document(DocumentType type, String value) {
        return type == DocumentType.CPF ? cpf(value) : cnpj(value);
    }

    public static String pixKey(PixKeyType type, String value) {
        return switch (type) {
            case CPF -> cpf(value);
            case CNPJ -> cnpj(value);
            case EMAIL -> email(value);
            case PHONE -> phone(value);
            case RANDOM -> "********-****-****-****-********" + value.substring(value.length() - 4);
        };
    }

    private static String cpf(String value) {
        return "***." + value.substring(3, 6) + "." + value.substring(6, 9) + "-**";
    }

    private static String cnpj(String value) {
        return "**." + value.substring(2, 5) + "." + value.substring(5, 8) + "/****-**";
    }

    private static String email(String value) {
        var at = value.indexOf('@');
        return value.charAt(0) + "***" + value.substring(at);
    }

    private static String phone(String value) {
        var ddd = value.substring(3, 5);
        var number = value.substring(5);
        var hidden = "*".repeat(number.length() - 4);
        return "+55 (" + ddd + ") " + hidden + "-" + number.substring(number.length() - 4);
    }
}
