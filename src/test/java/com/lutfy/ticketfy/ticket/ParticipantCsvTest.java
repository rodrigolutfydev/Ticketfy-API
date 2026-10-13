package com.lutfy.ticketfy.ticket;

import com.lutfy.ticketfy.user.Language;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantCsvTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static final List<ParticipantDTO> PARTICIPANTS = List.of(
            new ParticipantDTO(UUID.randomUUID(), UUID.randomUUID(), "Pista, frente", TicketStatus.USED,
                    Instant.parse("2026-03-10T02:30:00Z"), true, "=Carla Dias", "AB12"),
            new ParticipantDTO(UUID.randomUUID(), UUID.randomUUID(), "VIP", TicketStatus.VALID, null, false, "Ana Lima", "CD34"));

    @Test
    void writesPortugueseLabels() {
        var lines = ParticipantCsv.write(PARTICIPANTS, SAO_PAULO, Language.PT_BR).substring(1).split("\r\n");

        assertThat(lines[0]).isEqualTo("Participante;Lote;Situação;Check-in;Transferido;Final do código");
        assertThat(lines[1]).isEqualTo("'=Carla Dias;Pista, frente;Utilizado;09/03/2026 23:30:00;Sim;AB12");
        assertThat(lines[2]).isEqualTo("Ana Lima;VIP;Válido;;Não;CD34");
        assertThat(ParticipantCsv.filename("2026-10-13", Language.PT_BR)).isEqualTo("participantes-ticketfy-2026-10-13.csv");
    }

    @Test
    void writesEnglishLabelsWithTheSameTermsAsTheSite() {
        var lines = ParticipantCsv.write(PARTICIPANTS, SAO_PAULO, Language.EN).substring(1).split("\r\n");

        assertThat(lines[0]).isEqualTo("Attendee,Batch,Status,Check-in,Transferred,Code ending");
        assertThat(lines[1]).isEqualTo("'=Carla Dias,\"Pista, frente\",Used,2026-03-09 23:30:00,Yes,AB12");
        assertThat(lines[2]).isEqualTo("Ana Lima,VIP,Valid,,No,CD34");
        assertThat(ParticipantCsv.filename("2026-10-13", Language.EN)).isEqualTo("ticketfy-attendees-2026-10-13.csv");
    }
}
