package com.lutfy.ticketfy.payout.ledger;

import com.lutfy.ticketfy.infra.csv.CsvFormat;
import com.lutfy.ticketfy.user.Language;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LedgerCsv {

    private static final Map<Language, List<String>> HEADERS = Map.of(
            Language.PT_BR, List.of("Data", "Tipo", "Valor", "Evento", "Pedido", "Saque", "Lançamento"),
            Language.EN, List.of("Date", "Type", "Amount", "Event", "Order", "Withdrawal", "Entry"));
    private static final Map<Language, Map<LedgerEntryType, String>> TYPE_LABELS = Map.of(
            Language.PT_BR, Map.of(
                    LedgerEntryType.SALE_CREDIT, "Venda",
                    LedgerEntryType.REFUND_DEBIT, "Reembolso",
                    LedgerEntryType.PAYOUT_DEBIT, "Saque",
                    LedgerEntryType.PAYOUT_REVERSAL, "Estorno de saque"),
            Language.EN, Map.of(
                    LedgerEntryType.SALE_CREDIT, "Sale",
                    LedgerEntryType.REFUND_DEBIT, "Refund",
                    LedgerEntryType.PAYOUT_DEBIT, "Withdrawal",
                    LedgerEntryType.PAYOUT_REVERSAL, "Withdrawal reversal"));
    private static final Map<Language, String> FILE_PREFIX = Map.of(
            Language.PT_BR, "extrato-ticketfy-",
            Language.EN, "ticketfy-statement-");

    private LedgerCsv() {
    }

    public static String write(List<LedgerEntryDTO> entries, ZoneId zone, Language language) {
        var format = format(language);
        var labels = TYPE_LABELS.get(language);
        var csv = new StringBuilder(format.header(HEADERS.get(language)));
        for (var entry : entries) {
            csv.append(format.row(List.of(
                    format.dateTime(entry.createdAt(), zone),
                    format.text(labels.get(entry.type())),
                    format.number(entry.amount()),
                    format.text(entry.eventName()),
                    format.text(id(entry.orderId())),
                    format.text(id(entry.payoutId())),
                    format.text(id(entry.id())))));
        }
        return csv.toString();
    }

    public static String filename(String date, Language language) {
        return FILE_PREFIX.get(language) + date + ".csv";
    }

    static CsvFormat format(Language language) {
        return language == Language.EN ? CsvFormat.ENGLISH : CsvFormat.BRAZILIAN;
    }

    private static String id(UUID value) {
        return value == null ? null : value.toString();
    }
}
