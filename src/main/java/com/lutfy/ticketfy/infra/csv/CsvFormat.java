package com.lutfy.ticketfy.infra.csv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public enum CsvFormat {
    BRAZILIAN(";", ',', "dd/MM/yyyy HH:mm:ss"),
    ENGLISH(",", '.', "yyyy-MM-dd HH:mm:ss");

    private final String separator;
    private final char decimalSeparator;
    private final DateTimeFormatter dateTime;

    CsvFormat(String separator, char decimalSeparator, String dateTimePattern) {
        this.separator = separator;
        this.decimalSeparator = decimalSeparator;
        this.dateTime = DateTimeFormatter.ofPattern(dateTimePattern);
    }

    public String separator() {
        return separator;
    }

    public String text(String value) {
        return CsvCells.text(value, separator);
    }

    public String number(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', decimalSeparator);
    }

    public String dateTime(Instant value, ZoneId zone) {
        return value == null ? "" : dateTime.withZone(zone).format(value);
    }

    public String row(List<String> cells) {
        return String.join(separator, cells) + CsvCells.LINE_END;
    }

    public String header(List<String> names) {
        return CsvCells.BOM + row(names.stream().map(this::text).toList());
    }
}
