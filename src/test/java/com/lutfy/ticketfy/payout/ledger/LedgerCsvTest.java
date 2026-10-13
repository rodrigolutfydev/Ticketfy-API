package com.lutfy.ticketfy.payout.ledger;

import com.lutfy.ticketfy.infra.csv.CsvFormat;
import com.lutfy.ticketfy.user.Language;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerCsvTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void prefixesOnlyTextStartingWithFormulaTriggers() {
        for (var format : CsvFormat.values()) {
            assertThat(format.text("=SOMA(A1)")).isEqualTo("'=SOMA(A1)");
            assertThat(format.text("+5511")).isEqualTo("'+5511");
            assertThat(format.text("-Festa")).isEqualTo("'-Festa");
            assertThat(format.text("@importante")).isEqualTo("'@importante");
            assertThat(format.text("\tTab")).isEqualTo("'\tTab");
            assertThat(format.text("\rLinha")).isEqualTo("\"'\rLinha\"");
            assertThat(format.text("Show de Rock")).isEqualTo("Show de Rock");
            assertThat(format.text("Festa = alegria")).isEqualTo("Festa = alegria");
            assertThat(format.text("O \"Show\"")).isEqualTo("\"O \"\"Show\"\"\"");
            assertThat(format.text(null)).isEmpty();
        }
    }

    @Test
    void quotesTextContainingTheSeparatorOfEachFormat() {
        assertThat(CsvFormat.BRAZILIAN.text("Rock; Pop")).isEqualTo("\"Rock; Pop\"");
        assertThat(CsvFormat.BRAZILIAN.text("Rock, Pop")).isEqualTo("Rock, Pop");
        assertThat(CsvFormat.ENGLISH.text("Rock, Pop")).isEqualTo("\"Rock, Pop\"");
        assertThat(CsvFormat.ENGLISH.text("Rock; Pop")).isEqualTo("Rock; Pop");
        assertThat(CsvFormat.ENGLISH.text("=1+1, ok")).isEqualTo("\"'=1+1, ok\"");
    }

    @Test
    void writesNumbersWithTheDecimalSeparatorOfEachLanguage() {
        assertThat(CsvFormat.BRAZILIAN.number(new BigDecimal("-9.59"))).isEqualTo("-9,59");
        assertThat(CsvFormat.BRAZILIAN.number(new BigDecimal("1234.5"))).isEqualTo("1234,50");
        assertThat(CsvFormat.ENGLISH.number(new BigDecimal("-9.59"))).isEqualTo("-9.59");
        assertThat(CsvFormat.ENGLISH.number(new BigDecimal("1234.5"))).isEqualTo("1234.50");
    }

    @Test
    void writesPortugueseLabelsWithSemicolonsAndBrazilianDates() {
        var csv = LedgerCsv.write(List.of(entry()), SAO_PAULO, Language.PT_BR);

        assertThat(csv).startsWith("﻿Data;Tipo;Valor;Evento;Pedido;Saque;Lançamento\r\n");
        assertThat(csv).contains("09/03/2026 23:30:00;Reembolso;-9,59;\"Show; Rock\";");
        assertThat(LedgerCsv.filename("2026-10-13", Language.PT_BR)).isEqualTo("extrato-ticketfy-2026-10-13.csv");
    }

    @Test
    void writesEnglishLabelsWithCommasAndIsoDates() {
        var csv = LedgerCsv.write(List.of(entry()), SAO_PAULO, Language.EN);

        assertThat(csv).startsWith("﻿Date,Type,Amount,Event,Order,Withdrawal,Entry\r\n");
        assertThat(csv).contains("2026-03-09 23:30:00,Refund,-9.59,Show; Rock,");
        assertThat(csv).endsWith("\r\n");
        assertThat(LedgerCsv.filename("2026-10-13", Language.EN)).isEqualTo("ticketfy-statement-2026-10-13.csv");
    }

    private static LedgerEntryDTO entry() {
        return new LedgerEntryDTO(UUID.randomUUID(), LedgerEntryType.REFUND_DEBIT, new BigDecimal("-9.59"), UUID.randomUUID(),
                "Show; Rock", UUID.randomUUID(), null, Instant.parse("2026-03-10T02:30:00Z"));
    }
}
