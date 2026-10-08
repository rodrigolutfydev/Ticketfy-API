package com.lutfy.ticketfy.payout.ledger;

import com.lutfy.ticketfy.infra.exception.InvalidDateRangeException;
import com.lutfy.ticketfy.payout.PayoutSettings;
import com.lutfy.ticketfy.payout.admin.PayoutBlockRepository;
import com.lutfy.ticketfy.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class BalanceService {

    private final LedgerQueryRepository queries;
    private final PayoutBlockRepository blockRepository;
    private final PayoutSettings settings;
    private final Clock clock;
    private final ZoneId zone;

    public BalanceService(LedgerQueryRepository queries, PayoutBlockRepository blockRepository,
                         PayoutSettings settings, Clock clock,
                         @Value("${ticketfy.dashboard.time-zone}") String timeZone) {
        this.queries = queries;
        this.blockRepository = blockRepository;
        this.settings = settings;
        this.clock = clock;
        this.zone = ZoneId.of(timeZone);
    }

    @Transactional(readOnly = true)
    public BalanceDTO balance(User organizer) {
        var releasedUntil = clock.instant().minus(settings.releaseDelay());
        var balance = queries.findBalance(organizer.getId(), releasedUntil);
        return new BalanceDTO(money(balance.pending()), money(balance.available()), money(balance.inPayout()),
                money(balance.held()), money(balance.total()), settings.releaseDelayDays(), settings.minAmount(),
                blockRepository.existsById(organizer.getId()));
    }

    @Transactional(readOnly = true)
    public String exportLedger(User organizer, UUID eventId, LocalDate from, LocalDate to) {
        checkRange(from, to);
        return LedgerCsv.write(queries.findAllEntries(organizer.getId(), eventId, start(from), until(to)), zone);
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryDTO> ledger(User organizer, UUID eventId, LocalDate from, LocalDate to,
                                       Pageable pageable) {
        checkRange(from, to);
        Instant start = start(from);
        Instant until = until(to);
        return queries.findEntries(organizer.getId(), eventId, start, until,
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(entry -> new LedgerEntryDTO(entry.id(), entry.type(), money(entry.amount()), entry.eventId(),
                        entry.eventName(), entry.orderId(), entry.payoutId(), entry.createdAt()));
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("'from' must not be after 'to'");
        }
    }

    private Instant start(LocalDate from) {
        return from == null ? null : from.atStartOfDay(zone).toInstant();
    }

    private Instant until(LocalDate to) {
        return to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
