package com.lutfy.ticketfy.infra.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class IcsWriter {

    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final StringBuilder out = new StringBuilder();

    public IcsWriter raw(String name, String value) {
        return line(name + ":" + value);
    }

    public IcsWriter text(String name, String value) {
        return value == null || value.isBlank() ? this : raw(name, escape(value));
    }

    public IcsWriter time(String name, Instant value) {
        return value == null ? this : raw(name, formatUtc(value));
    }

    public String build() {
        return out.toString();
    }

    public static String formatUtc(Instant value) {
        return UTC.format(value.truncatedTo(ChronoUnit.SECONDS));
    }

    public static String escape(String value) {
        var escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case ';' -> escaped.append("\\;");
                case ',' -> escaped.append("\\,");
                case '\r' -> {
                    escaped.append("\\n");
                    if (i + 1 < value.length() && value.charAt(i + 1) == '\n') i++;
                }
                case '\n' -> escaped.append("\\n");
                default -> {
                    if (c == '\t' || !Character.isISOControl(c)) escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }

    private IcsWriter line(String content) {
        int octets = 0;
        int limit = MAX_LINE_OCTETS;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append(CRLF).append(' ');
                octets = 0;
                limit = MAX_LINE_OCTETS - 1;
            }
            out.appendCodePoint(codePoint);
            octets += size;
            i += Character.charCount(codePoint);
        }
        out.append(CRLF);
        return this;
    }
}
