package com.lutfy.ticketfy.payout.ledger;

import com.lutfy.ticketfy.order.Order;
import com.lutfy.ticketfy.payout.withdrawal.Payout;
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
        repository.saveAndFlush(LedgerEntry.forOrder(
                event.getOrganizer().getId(), event.getId(), order.getId(), type, amount, clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPayoutDebit(Payout payout) {
        repository.saveAndFlush(LedgerEntry.forPayout(payout.getOrganizerId(), payout.getId(),
                LedgerEntryType.PAYOUT_DEBIT, payout.getAmount().negate(), clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPayoutReversal(Payout payout) {
        repository.saveAndFlush(LedgerEntry.forPayout(payout.getOrganizerId(), payout.getId(),
                LedgerEntryType.PAYOUT_REVERSAL, payout.getAmount(), clock.instant()));
    }
}
