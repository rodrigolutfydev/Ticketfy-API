package com.lutfy.ticketfy.payout;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LedgerCsv {

    static final char BOM = '﻿';
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final List<String> HEADER = List.of("Data", "Tipo", "Valor", "Evento", "Pedido", "Saque", "Lançamento");
    private static final Map<LedgerEntryType, String> TYPE_LABELS = Map.of(
            LedgerEntryType.SALE_CREDIT, "Venda",
            LedgerEntryType.REFUND_DEBIT, "Reembolso",
            LedgerEntryType.PAYOUT_DEBIT, "Saque",
            LedgerEntryType.PAYOUT_REVERSAL, "Estorno de saque");

    private LedgerCsv() {
    }

    public static String write(List<LedgerEntryDTO> entries, ZoneId zone) {
        var csv = new StringBuilder().append(BOM);
        csv.append(String.join(SEPARATOR, HEADER.stream().map(LedgerCsv::text).toList())).append(LINE_END);
        var formatter = DATE_TIME.withZone(zone);
        for (var entry : entries) {
            csv.append(String.join(SEPARATOR, List.of(
                    formatter.format(entry.createdAt()),
                    text(TYPE_LABELS.get(entry.type())),
                    number(entry.amount()),
                    text(entry.eventName()),
                    text(id(entry.orderId())),
                    text(id(entry.payoutId())),
                    text(id(entry.id()))))).append(LINE_END);
        }
        return csv.toString();
    }

    static String text(String value) {
        if (value == null || value.isEmpty()) return "";
        var safe = startsWithFormulaTrigger(value) ? "'" + value : value;
        if (safe.contains(SEPARATOR) || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    static String number(BigDecimal value) {
        return value.setScale(2).toPlainString().replace('.', ',');
    }

    private static boolean startsWithFormulaTrigger(String value) {
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> true;
            default -> false;
        };
    }

    private static String id(UUID value) {
        return value == null ? null : value.toString();
    }
}
