package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.infra.csv.CsvFormat;
import com.lutfy.ticketfy.user.Language;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public final class ParticipantCsv {

    private static final Map<Language, List<String>> HEADERS = Map.of(
            Language.PT_BR, List.of("Participante", "Lote", "Situação", "Check-in", "Transferido", "Final do código"),
            Language.EN, List.of("Attendee", "Batch", "Status", "Check-in", "Transferred", "Code ending"));
    private static final Map<Language, Map<TicketStatus, String>> STATUS_LABELS = Map.of(
            Language.PT_BR, Map.of(
                    TicketStatus.VALID, "Válido",
                    TicketStatus.USED, "Utilizado",
                    TicketStatus.CANCELLED, "Cancelado"),
            Language.EN, Map.of(
                    TicketStatus.VALID, "Valid",
                    TicketStatus.USED, "Used",
                    TicketStatus.CANCELLED, "Cancelled"));
    private static final Map<Language, List<String>> YES_NO = Map.of(
            Language.PT_BR, List.of("Sim", "Não"),
            Language.EN, List.of("Yes", "No"));
    private static final Map<Language, String> FILE_PREFIX = Map.of(
            Language.PT_BR, "participantes-ticketfy-",
            Language.EN, "ticketfy-attendees-");

    private ParticipantCsv() {
    }

    public static String write(List<ParticipantDTO> participants, ZoneId zone, Language language) {
        var format = language == Language.EN ? CsvFormat.ENGLISH : CsvFormat.BRAZILIAN;
        var labels = STATUS_LABELS.get(language);
        var yesNo = YES_NO.get(language);
        var csv = new StringBuilder(format.header(HEADERS.get(language)));
        for (var participant : participants) {
            csv.append(format.row(List.of(
                    format.text(participant.holderName()),
                    format.text(participant.ticketTypeName()),
                    format.text(labels.get(participant.status())),
                    format.dateTime(participant.checkedInAt(), zone),
                    format.text(participant.transferred() ? yesNo.get(0) : yesNo.get(1)),
                    format.text(participant.codeSuffix()))));
        }
        return csv.toString();
    }

    public static String filename(String date, Language language) {
        return FILE_PREFIX.get(language) + date + ".csv";
    }
}
