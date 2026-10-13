package com.lutfy.ticketfy.infra.calendar;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class IcsWriterTest {

    @Test
    void escapesSpecialCharactersAndEveryKindOfLineBreak() {
        assertThat(IcsWriter.escape("a\\b;c,d\ne\r\nf\rg\th\u0007i")).isEqualTo("a\\\\b\\;c\\,d\\ne\\nf\\ng\thi");
    }

    @Test
    void foldsLongLinesAt75OctetsWithoutSplittingMultibyteCharacters() {
        var value = "ç".repeat(100);
        var ics = new IcsWriter().text("DESCRIPTION", value).build();

        assertThat(ics).endsWith("\r\n");
        var lines = ics.split("\r\n");
        assertThat(lines.length).isGreaterThan(1);
        assertThat(Arrays.stream(lines)).allMatch(line -> line.getBytes(StandardCharsets.UTF_8).length <= 75);
        assertThat(Arrays.stream(lines).skip(1)).allMatch(line -> line.startsWith(" "));
        assertThat(ics.replace("\r\n ", "")).isEqualTo("DESCRIPTION:" + value + "\r\n");
    }

    @Test
    void writesTimesInUtcAndSkipsEmptyValues() {
        var ics = new IcsWriter()
                .time("DTSTART", Instant.parse("2026-12-10T23:00:00.789Z"))
                .time("DTEND", null)
                .text("LOCATION", "  ")
                .build();

        assertThat(ics).isEqualTo("DTSTART:20261210T230000Z\r\n");
    }
}
