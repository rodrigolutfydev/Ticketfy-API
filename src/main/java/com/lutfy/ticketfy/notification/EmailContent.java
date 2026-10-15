package com.lutfy.ticketfy.notification;

import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;

// Corpo do e-mail em blocos simples, que viram o HTML (com a identidade do
// Ticketfy) e a versão em texto puro a partir do mesmo conteúdo.
final class EmailContent {

    private static final String ACCENT = "#ff5b1f";
    private static final String INK = "#0b0a0c";
    private static final String PAPER = "#f4f1f6";
    private static final String MUTED = "#5b5763";
    private static final String LINE = "#e4e0e8";

    private sealed interface Block permits Paragraph, Details {
    }

    private record Paragraph(String text) implements Block {
    }

    private record Details(String title, List<String[]> rows) implements Block {
    }

    private final String lang;
    private final String heading;
    private final List<Block> blocks = new ArrayList<>();
    private String buttonLabel;
    private String buttonUrl;
    private String footer;

    EmailContent(String lang, String heading) {
        this.lang = lang;
        this.heading = heading;
    }

    EmailContent paragraph(String text) {
        blocks.add(new Paragraph(text));
        return this;
    }

    /** Pares rótulo/valor; {@code title} pode ser nulo. */
    EmailContent details(String title, List<String[]> rows) {
        blocks.add(new Details(title, List.copyOf(rows)));
        return this;
    }

    EmailContent button(String label, String url) {
        this.buttonLabel = label;
        this.buttonUrl = url;
        return this;
    }

    EmailContent footer(String text) {
        this.footer = text;
        return this;
    }

    String text() {
        var out = new StringBuilder("Ticketfy\n\n").append(heading).append("\n\n");
        for (var block : blocks) {
            if (block instanceof Paragraph p) {
                out.append(p.text()).append("\n\n");
            } else if (block instanceof Details d) {
                if (d.title() != null) out.append(d.title()).append('\n');
                for (var row : d.rows()) out.append("- ").append(row[0]).append(": ").append(row[1]).append('\n');
                out.append('\n');
            }
        }
        if (buttonUrl != null) out.append(buttonLabel).append(": ").append(buttonUrl).append("\n\n");
        if (footer != null) out.append("--\n").append(footer).append('\n');
        return out.toString();
    }

    String html() {
        var body = new StringBuilder();
        body.append("<h1 style=\"margin:0 0 16px;font-size:22px;line-height:1.3;color:").append(INK).append("\">")
                .append(esc(heading)).append("</h1>");
        for (var block : blocks) {
            if (block instanceof Paragraph p) {
                body.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.5;color:")
                        .append(INK).append("\">").append(esc(p.text())).append("</p>");
            } else if (block instanceof Details d) {
                if (d.title() != null) {
                    body.append("<h2 style=\"margin:8px 0 8px;font-size:16px;color:").append(INK).append("\">")
                            .append(esc(d.title())).append("</h2>");
                }
                body.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                        .append("style=\"margin:0 0 16px;border-collapse:collapse\">");
                for (var row : d.rows()) {
                    body.append("<tr><td style=\"padding:8px 12px 8px 0;border-bottom:1px solid ").append(LINE)
                            .append(";font-size:14px;color:").append(MUTED).append(";vertical-align:top;white-space:nowrap\">")
                            .append(esc(row[0])).append("</td><td style=\"padding:8px 0;border-bottom:1px solid ")
                            .append(LINE).append(";font-size:14px;color:").append(INK).append(";font-weight:600\">")
                            .append(esc(row[1])).append("</td></tr>");
                }
                body.append("</table>");
            }
        }
        if (buttonUrl != null) {
            body.append("<p style=\"margin:24px 0\"><a href=\"").append(esc(buttonUrl))
                    .append("\" style=\"display:inline-block;padding:12px 22px;border-radius:999px;background:")
                    .append(ACCENT).append(";color:").append(INK)
                    .append(";font-weight:700;font-size:15px;text-decoration:none\">")
                    .append(esc(buttonLabel)).append("</a></p>");
        }
        return """
                <!doctype html>
                <html lang="%s"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">\
                <title>%s</title></head>
                <body style="margin:0;padding:0;background:%s;font-family:Arial,Helvetica,sans-serif">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s">
                <tr><td align="center" style="padding:24px 12px">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden">
                <tr><td style="background:%s;padding:18px 24px;font-size:20px;font-weight:800;letter-spacing:-0.02em;color:%s">Ticketfy</td></tr>
                <tr><td style="padding:24px">%s</td></tr>
                %s
                </table></td></tr></table>
                </body></html>
                """.formatted(lang, esc(heading), PAPER, PAPER, INK, ACCENT, body,
                footer == null ? "" : "<tr><td style=\"padding:16px 24px;border-top:1px solid " + LINE
                        + ";font-size:12px;line-height:1.5;color:" + MUTED + "\">" + esc(footer) + "</td></tr>");
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
