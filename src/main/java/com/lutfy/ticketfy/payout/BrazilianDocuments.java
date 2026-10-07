package com.lutfy.ticketfy.payout;

import java.util.Locale;
import java.util.regex.Pattern;

public final class BrazilianDocuments {

    private static final Pattern CPF = Pattern.compile("\\d{11}");
    private static final Pattern CNPJ = Pattern.compile("[0-9A-Z]{12}\\d{2}");
    private static final Pattern SEPARATORS = Pattern.compile("[.\\-/\\s]");
    private static final int[] CNPJ_FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private BrazilianDocuments() {
    }

    public static String normalize(String value) {
        return value == null ? null : SEPARATORS.matcher(value).replaceAll("").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(DocumentType type, String value) {
        var normalized = normalize(value);
        if (normalized == null) return false;
        return type == DocumentType.CPF ? isValidCpf(normalized) : isValidCnpj(normalized);
    }

    static boolean isValidCpf(String digits) {
        if (!CPF.matcher(digits).matches() || allSame(digits)) return false;
        return checkDigit(digits, 9, 10) == digits.charAt(9) - '0'
                && checkDigit(digits, 10, 11) == digits.charAt(10) - '0';
    }

    static boolean isValidCnpj(String value) {
        if (!CNPJ.matcher(value).matches() || allSame(value)) return false;
        return cnpjDigit(value, CNPJ_FIRST_WEIGHTS) == value.charAt(12) - '0'
                && cnpjDigit(value, CNPJ_SECOND_WEIGHTS) == value.charAt(13) - '0';
    }

    private static int checkDigit(String digits, int length, int firstWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (firstWeight - i);
        }
        return modElevenDigit(sum);
    }

    private static int cnpjDigit(String value, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (value.charAt(i) - '0') * weights[i];
        }
        return modElevenDigit(sum);
    }

    private static int modElevenDigit(int sum) {
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static boolean allSame(String value) {
        return value.chars().distinct().count() == 1;
    }
}
