package com.lutfy.ticketfy.payout;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerCsvTest {

    @Test
    void prefixesOnlyTextStartingWithFormulaTriggers() {
        assertThat(LedgerCsv.text("=SOMA(A1)")).isEqualTo("'=SOMA(A1)");
        assertThat(LedgerCsv.text("+5511")).isEqualTo("'+5511");
        assertThat(LedgerCsv.text("-Festa")).isEqualTo("'-Festa");
        assertThat(LedgerCsv.text("@importante")).isEqualTo("'@importante");
        assertThat(LedgerCsv.text("\tTab")).isEqualTo("'\tTab");
        assertThat(LedgerCsv.text("\rLinha")).isEqualTo("\"'\rLinha\"");
        assertThat(LedgerCsv.text("Show de Rock")).isEqualTo("Show de Rock");
        assertThat(LedgerCsv.text("Festa = alegria")).isEqualTo("Festa = alegria");
        assertThat(LedgerCsv.text("Rock; Pop")).isEqualTo("\"Rock; Pop\"");
        assertThat(LedgerCsv.text("O \"Show\"")).isEqualTo("\"O \"\"Show\"\"\"");
        assertThat(LedgerCsv.text(null)).isEmpty();
    }

    @Test
    void writesNumbersWithCommaAndNoPrefix() {
        assertThat(LedgerCsv.number(new BigDecimal("-9.59"))).isEqualTo("-9,59");
        assertThat(LedgerCsv.number(new BigDecimal("1234.5"))).isEqualTo("1234,50");

        var csv = LedgerCsv.write(List.of(new LedgerEntryDTO(UUID.randomUUID(), LedgerEntryType.REFUND_DEBIT,
                new BigDecimal("-9.59"), UUID.randomUUID(), "Show", UUID.randomUUID(), null,
                Instant.parse("2026-03-10T02:30:00Z"))), ZoneId.of("America/Sao_Paulo"));

        assertThat(csv).startsWith("﻿Data;Tipo;Valor;Evento;Pedido;Saque;Lançamento\r\n");
        assertThat(csv).contains("09/03/2026 23:30:00;Reembolso;-9,59;Show;");
    }
}
