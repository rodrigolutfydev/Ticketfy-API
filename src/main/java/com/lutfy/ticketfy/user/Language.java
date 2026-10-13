package com.lutfy.ticketfy.user;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum Language {
    PT_BR("pt-BR"),
    EN("en");

    public static final Language DEFAULT = PT_BR;

    private final String code;

    Language(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static Optional<Language> fromCode(String code) {
        return Arrays.stream(values()).filter(language -> language.code.equals(code)).findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(Language::code).toList();
    }
}
