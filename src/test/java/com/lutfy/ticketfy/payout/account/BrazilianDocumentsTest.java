package com.lutfy.ticketfy.payout.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrazilianDocumentsTest {

    @Test
    void validatesCpfCheckDigits() {
        assertThat(BrazilianDocuments.isValid(DocumentType.CPF, "529.982.247-25")).isTrue();
        assertThat(BrazilianDocuments.isValid(DocumentType.CPF, "11144477735")).isTrue();
        assertThat(BrazilianDocuments.isValid(DocumentType.CPF, "52998224724")).isFalse();
        assertThat(BrazilianDocuments.isValid(DocumentType.CPF, "00000000000")).isFalse();
        assertThat(BrazilianDocuments.isValid(DocumentType.CPF, "5299822472")).isFalse();
        assertThat(BrazilianDocuments.isValid(DocumentType.CPF, "5299822472A")).isFalse();
    }

    @Test
    void validatesNumericAndAlphanumericCnpj() {
        assertThat(BrazilianDocuments.isValid(DocumentType.CNPJ, "11.222.333/0001-81")).isTrue();
        assertThat(BrazilianDocuments.isValid(DocumentType.CNPJ, "12.ABC.345/01DE-35")).isTrue();
        assertThat(BrazilianDocuments.isValid(DocumentType.CNPJ, "12abc34501de35")).isTrue();
        assertThat(BrazilianDocuments.isValid(DocumentType.CNPJ, "11222333000182")).isFalse();
        assertThat(BrazilianDocuments.isValid(DocumentType.CNPJ, "12ABC34501DE53")).isFalse();
        assertThat(BrazilianDocuments.isValid(DocumentType.CNPJ, "12ABC34501DEAB")).isFalse();
        assertThat(BrazilianDocuments.isValid(DocumentType.CNPJ, "00000000000000")).isFalse();
        assertThat(BrazilianDocuments.isValid(DocumentType.CNPJ, "52998224725")).isFalse();
    }

    @Test
    void normalizesPixKeysByType() {
        assertThat(PixKeys.normalize(PixKeyType.EMAIL, " Maria@Example.COM ")).contains("maria@example.com");
        assertThat(PixKeys.normalize(PixKeyType.PHONE, "+55 (21) 3456-7890")).contains("+552134567890");
        assertThat(PixKeys.normalize(PixKeyType.PHONE, "+55 11 98765-4321")).contains("+5511987654321");
        assertThat(PixKeys.normalize(PixKeyType.PHONE, "+1 202 555 0100")).isEmpty();
        assertThat(PixKeys.normalize(PixKeyType.RANDOM, "123E4567-E89B-12D3-A456-426614174000"))
                .contains("123e4567-e89b-12d3-a456-426614174000");
        assertThat(PixKeys.normalize(PixKeyType.RANDOM, "not-a-uuid")).isEmpty();
        assertThat(PixKeys.normalize(PixKeyType.CPF, "529.982.247-25")).contains("52998224725");
        assertThat(PixKeys.normalize(PixKeyType.EMAIL, "a".repeat(70) + "@mail.com")).isEmpty();
    }
}
