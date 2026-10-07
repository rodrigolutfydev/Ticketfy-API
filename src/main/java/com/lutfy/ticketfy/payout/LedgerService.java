package com.lutfy.ticketfy.payout;

import com.lutfy.ticketfy.order.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;

@Service
public class LedgerService {

    private final LedgerEntryRepository repository;
    private final Clock clock;

    public LedgerService(LedgerEntryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSale(Order order) {
        record(order, LedgerEntryType.SALE_CREDIT, order.getNetAmount());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRefund(Order order) {
        record(order, LedgerEntryType.REFUND_DEBIT, order.getNetAmount().negate());
    }

    private void record(Order order, LedgerEntryType type, BigDecimal amount) {
        if (amount.signum() == 0) {
            return;
        }
        var event = order.event();
        repository.saveAndFlush(new LedgerEntry(
                event.getOrganizer().getId(), event.getId(), order.getId(), type, amount, clock.instant()));
    }
}
