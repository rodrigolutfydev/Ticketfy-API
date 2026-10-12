package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.infra.csv.CsvCells;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class ParticipantCsv {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final List<String> HEADER = List.of("Participante", "Lote", "Situação", "Check-in", "Transferido",
            "Final do código");
    private static final Map<TicketStatus, String> STATUS_LABELS = Map.of(
            TicketStatus.VALID, "Válido",
            TicketStatus.USED, "Usado",
            TicketStatus.CANCELLED, "Cancelado");

    private ParticipantCsv() {
    }

    public static String write(List<ParticipantDTO> participants, ZoneId zone) {
        var csv = new StringBuilder().append(CsvCells.BOM);
        csv.append(String.join(CsvCells.SEPARATOR, HEADER.stream().map(CsvCells::text).toList())).append(CsvCells.LINE_END);
        var formatter = DATE_TIME.withZone(zone);
        for (var participant : participants) {
            csv.append(String.join(CsvCells.SEPARATOR, List.of(
                    CsvCells.text(participant.holderName()),
                    CsvCells.text(participant.ticketTypeName()),
                    CsvCells.text(STATUS_LABELS.get(participant.status())),
                    participant.checkedInAt() == null ? "" : formatter.format(participant.checkedInAt()),
                    participant.transferred() ? "Sim" : "Não",
                    CsvCells.text(participant.codeSuffix())))).append(CsvCells.LINE_END);
        }
        return csv.toString();
    }
}
