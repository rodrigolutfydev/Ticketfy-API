package com.lutfy.ticketfy.notification;

import com.lutfy.ticketfy.auth.passwordreset.PasswordResetSettings;
import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.event.EventCalendarService;
import com.lutfy.ticketfy.infra.config.SiteSettings;
import com.lutfy.ticketfy.infra.mail.EmailAttachment;
import com.lutfy.ticketfy.infra.mail.EmailMessage;
import com.lutfy.ticketfy.infra.mail.MailSettings;
import com.lutfy.ticketfy.infra.qr.QrCodePng;
import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.order.OrderStatus;
import com.lutfy.ticketfy.ticket.Ticket;
import com.lutfy.ticketfy.ticket.TicketRepository;
import com.lutfy.ticketfy.ticket.TicketStatus;
import com.lutfy.ticketfy.user.User;
import com.lutfy.ticketfy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Monta os e-mails transacionais a partir do estado já gravado. Roda fora da
// transação que originou o envio, em uma transação só de leitura própria.
// Nunca registra no log o código do ingresso nem o conteúdo do QR.
@Component
public class TransactionalEmails {

    static final Duration DEFAULT_EVENT_DURATION = Duration.ofHours(3);

    private static final Logger log = LoggerFactory.getLogger(TransactionalEmails.class);

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final EventCalendarService calendarService;
    private final QrCodePng qrCodes;
    private final SiteSettings site;
    private final MailSettings settings;
    private final PasswordResetSettings resetSettings;

    public TransactionalEmails(OrderRepository orderRepository, TicketRepository ticketRepository,
                               UserRepository userRepository, EventCalendarService calendarService,
                               QrCodePng qrCodes, SiteSettings site, MailSettings settings,
                               PasswordResetSettings resetSettings) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.calendarService = calendarService;
        this.qrCodes = qrCodes;
        this.site = site;
        this.settings = settings;
        this.resetSettings = resetSettings;
    }

    @Transactional(readOnly = true)
    public Optional<EmailMessage> purchaseConfirmation(UUID orderId) {
        var order = orderRepository.findWithItemsById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PAID || !reachable(order.getUser())) {
            return Optional.empty();
        }
        var buyer = order.getUser();
        var event = order.event();
        var texts = MailTexts.of(buyer.getLanguage());
        var tickets = ticketRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.VALID && ticket.getOwner().getId().equals(buyer.getId()))
                .toList();

        var ticketRows = tickets.stream()
                .map(ticket -> new String[]{ticket.getTicketType().getName(), ticket.getCode()})
                .toList();
        var content = new EmailContent(texts.lang(), texts.purchaseHeading())
                .paragraph(texts.greeting(buyer.getName()))
                .paragraph(texts.purchaseIntro())
                .details(null, eventRows(event, texts))
                .details(texts.ticketsTitle(), ticketRows)
                .details(null, List.<String[]>of(new String[]{texts.totalPaid(),
                        order.isFree() ? texts.free() : texts.money(order.getTotalAmount())}))
                .paragraph(texts.purchaseAttachments())
                .footer(texts.footer());
        site.ticketsUrl().ifPresent(url -> content.button(texts.viewTickets(), url));

        var attachments = new ArrayList<EmailAttachment>();
        for (int i = 0; i < tickets.size(); i++) {
            qrAttachment(tickets.get(i), "ticket-" + (i + 1) + ".png").ifPresent(attachments::add);
        }
        calendarAttachment(event).ifPresent(attachments::add);
        return Optional.of(new EmailMessage(buyer.getEmail(), buyer.getName(),
                texts.purchaseSubject(event.getName()), content.html(), content.text(), attachments));
    }

    @Transactional(readOnly = true)
    public Optional<EmailMessage> transferReceived(UUID ticketId, UUID senderId, UUID recipientId) {
        var ticket = ticketRepository.findById(ticketId).orElse(null);
        var sender = userRepository.findById(senderId).orElse(null);
        // Se o ingresso já mudou de dono ou de estado, o QR deste e-mail não valeria.
        if (ticket == null || sender == null || ticket.getStatus() != TicketStatus.VALID
                || !ticket.getOwner().getId().equals(recipientId) || !reachable(ticket.getOwner())) {
            return Optional.empty();
        }
        var recipient = ticket.getOwner();
        var event = ticket.getTicketType().getEvent();
        var texts = MailTexts.of(recipient.getLanguage());
        var rows = new ArrayList<>(eventRows(event, texts));
        rows.add(new String[]{texts.ticketType(), ticket.getTicketType().getName()});
        rows.add(new String[]{texts.code(), ticket.getCode()});
        var content = new EmailContent(texts.lang(), texts.transferReceivedHeading())
                .paragraph(texts.greeting(recipient.getName()))
                .paragraph(texts.transferReceivedIntro(sender.getName()))
                .details(null, rows)
                .paragraph(texts.transferReceivedAttachment())
                .footer(texts.footer());
        site.ticketsUrl().ifPresent(url -> content.button(texts.viewTickets(), url));
        var attachments = qrAttachment(ticket, "ticket.png").map(List::of).orElse(List.of());
        return Optional.of(new EmailMessage(recipient.getEmail(), recipient.getName(),
                texts.transferReceivedSubject(sender.getName()), content.html(), content.text(), attachments));
    }

    @Transactional(readOnly = true)
    public Optional<EmailMessage> transferSent(UUID ticketId, UUID senderId, UUID recipientId) {
        var ticket = ticketRepository.findById(ticketId).orElse(null);
        var sender = userRepository.findById(senderId).orElse(null);
        var recipient = userRepository.findById(recipientId).orElse(null);
        if (ticket == null || recipient == null || !reachable(sender)) {
            return Optional.empty();
        }
        var event = ticket.getTicketType().getEvent();
        var texts = MailTexts.of(sender.getLanguage());
        var rows = new ArrayList<>(eventRows(event, texts));
        rows.add(new String[]{texts.ticketType(), ticket.getTicketType().getName()});
        var content = new EmailContent(texts.lang(), texts.transferSentHeading())
                .paragraph(texts.greeting(sender.getName()))
                .paragraph(texts.transferSentIntro(recipient.getName()))
                .details(null, rows)
                .paragraph(texts.transferSentOldQr())
                .footer(texts.footer());
        return Optional.of(new EmailMessage(sender.getEmail(), sender.getName(),
                texts.transferSentSubject(event.getName()), content.html(), content.text(), List.of()));
    }

    @Transactional(readOnly = true)
    public Optional<EmailMessage> passwordReset(UUID userId, String rawToken) {
        var user = userRepository.findById(userId).orElse(null);
        var link = site.passwordResetUrl(rawToken);
        if (!reachable(user) || user.isSuspended()) {
            return Optional.empty();
        }
        if (link.isEmpty()) {
            log.warn("Password reset email for user {} skipped: SITE_BASE_URL is not set", userId);
            return Optional.empty();
        }
        var texts = MailTexts.of(user.getLanguage());
        boolean firstPassword = !user.hasPassword();
        var content = new EmailContent(texts.lang(), texts.resetHeading(firstPassword))
                .paragraph(texts.greeting(user.getName()))
                .paragraph(texts.resetIntro(firstPassword))
                .button(texts.resetButton(firstPassword), link.get())
                .paragraph(texts.resetExpiry(resetSettings.tokenTtl().toMinutes()))
                .paragraph(texts.resetIgnore())
                .footer(texts.footer());
        return Optional.of(new EmailMessage(user.getEmail(), user.getName(), texts.resetSubject(firstPassword),
                content.html(), content.text(), List.of()));
    }

    private List<String[]> eventRows(Event event, MailTexts texts) {
        var rows = new ArrayList<String[]>();
        rows.add(new String[]{texts.event(), event.getName()});
        rows.add(new String[]{texts.date(), texts.date(event.getStartsAt(), settings.timeZone())});
        rows.add(new String[]{texts.time(), texts.time(event.getStartsAt(), settings.timeZone())});
        var location = event.fullLocation();
        if (!location.isEmpty()) {
            rows.add(new String[]{texts.location(), location});
        }
        return rows;
    }

    private Optional<EmailAttachment> qrAttachment(Ticket ticket, String fileName) {
        try {
            return Optional.of(new EmailAttachment(fileName, "image/png", qrCodes.render(ticket.getCode())));
        } catch (RuntimeException ex) {
            log.warn("QR code attachment skipped for ticket {}: {}", ticket.getId(), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<EmailAttachment> calendarAttachment(Event event) {
        try {
            var ics = calendarService.calendar(event, DEFAULT_EVENT_DURATION);
            return Optional.of(new EmailAttachment("event.ics", "text/calendar",
                    ics.getBytes(StandardCharsets.UTF_8)));
        } catch (RuntimeException ex) {
            log.warn("Calendar attachment skipped for event {}: {}", event.getId(), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static boolean reachable(User user) {
        return user != null && user.getDeletedAt() == null && user.getEmail() != null && !user.getEmail().isBlank();
    }
}
