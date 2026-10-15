package com.lutfy.ticketfy.notification;

import com.lutfy.ticketfy.user.Language;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Currency;
import java.util.Locale;

// Textos dos e-mails nos idiomas da conta (pt-BR e en), com datas e valores
// no formato de cada um.
abstract class MailTexts {

    static MailTexts of(Language language) {
        return language == Language.EN ? new English() : new Portuguese();
    }

    abstract String lang();

    abstract Locale locale();

    abstract String datePattern();

    abstract String timePattern();

    String date(Instant instant, ZoneId zone) {
        return DateTimeFormatter.ofPattern(datePattern(), locale()).withZone(zone).format(instant);
    }

    String time(Instant instant, ZoneId zone) {
        return DateTimeFormatter.ofPattern(timePattern(), locale()).withZone(zone).format(instant)
                + " (" + zone.getDisplayName(TextStyle.FULL, locale()) + ")";
    }

    String money(BigDecimal amount) {
        var format = NumberFormat.getCurrencyInstance(locale());
        format.setCurrency(Currency.getInstance("BRL"));
        return format.format(amount);
    }

    abstract String greeting(String name);

    abstract String event();

    abstract String date();

    abstract String time();

    abstract String location();

    abstract String ticketType();

    abstract String code();

    abstract String viewTickets();

    abstract String footer();

    abstract String purchaseSubject(String eventName);

    abstract String purchaseHeading();

    abstract String purchaseIntro();

    abstract String ticketsTitle();

    abstract String totalPaid();

    abstract String free();

    abstract String purchaseAttachments();

    abstract String transferReceivedSubject(String senderName);

    abstract String transferReceivedHeading();

    abstract String transferReceivedIntro(String senderName);

    abstract String transferReceivedAttachment();

    abstract String transferSentSubject(String eventName);

    abstract String transferSentHeading();

    abstract String transferSentIntro(String recipientName);

    abstract String transferSentOldQr();

    private static final class Portuguese extends MailTexts {
        String lang() { return "pt-BR"; }
        Locale locale() { return Locale.forLanguageTag("pt-BR"); }
        String datePattern() { return "EEEE, d 'de' MMMM 'de' uuuu"; }
        String timePattern() { return "HH:mm"; }
        String greeting(String name) { return "Olá, " + name + "!"; }
        String event() { return "Evento"; }
        String date() { return "Data"; }
        String time() { return "Horário"; }
        String location() { return "Local"; }
        String ticketType() { return "Lote"; }
        String code() { return "Código"; }
        String viewTickets() { return "Ver meus ingressos"; }
        String footer() { return "Você recebeu este e-mail porque tem uma conta no Ticketfy."; }
        String purchaseSubject(String eventName) { return "Compra confirmada: " + eventName; }
        String purchaseHeading() { return "Compra confirmada"; }
        String purchaseIntro() { return "Seu pedido está confirmado. Os ingressos já estão na sua conta."; }
        String ticketsTitle() { return "Ingressos"; }
        String totalPaid() { return "Total pago"; }
        String free() { return "Gratuito"; }
        String purchaseAttachments() {
            return "O QR Code de cada ingresso vai em anexo, junto com o arquivo do evento para a sua agenda (.ics). Apresente o QR Code na entrada.";
        }
        String transferReceivedSubject(String senderName) { return senderName + " transferiu um ingresso para você"; }
        String transferReceivedHeading() { return "Você recebeu um ingresso"; }
        String transferReceivedIntro(String senderName) { return senderName + " transferiu um ingresso para a sua conta."; }
        String transferReceivedAttachment() { return "O QR Code do ingresso vai em anexo. Apresente-o na entrada."; }
        String transferSentSubject(String eventName) { return "Transferência concluída: " + eventName; }
        String transferSentHeading() { return "Transferência concluída"; }
        String transferSentIntro(String recipientName) { return "Você transferiu um ingresso para " + recipientName + "."; }
        String transferSentOldQr() { return "O QR Code antigo deste ingresso deixou de valer e não passa mais na entrada."; }
    }

    private static final class English extends MailTexts {
        String lang() { return "en"; }
        Locale locale() { return Locale.US; }
        String datePattern() { return "EEEE, MMMM d, uuuu"; }
        String timePattern() { return "h:mm a"; }
        String greeting(String name) { return "Hi, " + name + "!"; }
        String event() { return "Event"; }
        String date() { return "Date"; }
        String time() { return "Time"; }
        String location() { return "Venue"; }
        String ticketType() { return "Ticket type"; }
        String code() { return "Code"; }
        String viewTickets() { return "View my tickets"; }
        String footer() { return "You received this email because you have a Ticketfy account."; }
        String purchaseSubject(String eventName) { return "Purchase confirmed: " + eventName; }
        String purchaseHeading() { return "Purchase confirmed"; }
        String purchaseIntro() { return "Your order is confirmed. The tickets are already in your account."; }
        String ticketsTitle() { return "Tickets"; }
        String totalPaid() { return "Total paid"; }
        String free() { return "Free"; }
        String purchaseAttachments() {
            return "Each ticket's QR code is attached, along with the event file for your calendar (.ics). Show the QR code at the entrance.";
        }
        String transferReceivedSubject(String senderName) { return senderName + " transferred a ticket to you"; }
        String transferReceivedHeading() { return "You received a ticket"; }
        String transferReceivedIntro(String senderName) { return senderName + " transferred a ticket to your account."; }
        String transferReceivedAttachment() { return "The ticket's QR code is attached. Show it at the entrance."; }
        String transferSentSubject(String eventName) { return "Transfer completed: " + eventName; }
        String transferSentHeading() { return "Transfer completed"; }
        String transferSentIntro(String recipientName) { return "You transferred a ticket to " + recipientName + "."; }
        String transferSentOldQr() { return "This ticket's old QR code is no longer valid and will not be accepted at the entrance."; }
    }
}
