package com.lutfy.ticketfy.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class LanguageConverter implements AttributeConverter<Language, String> {

    @Override
    public String convertToDatabaseColumn(Language language) {
        return language == null ? null : language.code();
    }

    @Override
    public Language convertToEntityAttribute(String code) {
        return code == null ? null : Language.fromCode(code)
                .orElseThrow(() -> new IllegalStateException("Unknown language: " + code));
    }
}
