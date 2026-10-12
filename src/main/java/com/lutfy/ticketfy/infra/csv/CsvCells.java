package com.lutfy.ticketfy.infra.csv;

public final class CsvCells {

    public static final char BOM = '﻿';
    public static final String SEPARATOR = ";";
    public static final String LINE_END = "\r\n";

    private CsvCells() {
    }

    public static String text(String value) {
        if (value == null || value.isEmpty()) return "";
        var safe = startsWithFormulaTrigger(value) ? "'" + value : value;
        if (safe.contains(SEPARATOR) || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static boolean startsWithFormulaTrigger(String value) {
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> true;
            default -> false;
        };
    }
}
