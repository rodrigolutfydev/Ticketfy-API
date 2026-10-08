package com.lutfy.ticketfy.payment;

import com.lutfy.ticketfy.infra.exception.InvalidEventStateException;
import com.lutfy.ticketfy.infra.exception.InvalidOrderStateException;
import com.lutfy.ticketfy.infra.exception.InvalidPaymentStateException;
import com.lutfy.ticketfy.infra.exception.OrderNotFoundException;
import com.lutfy.ticketfy.order.Order;
import com.lutfy.ticketfy.order.OrderRepository;
import com.lutfy.ticketfy.payout.ledger.LedgerService;
import com.lutfy.ticketfy.ticket.TicketService;
import com.lutfy.ticketfy.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TicketService ticketService;
    private final PaymentGateway paymentGateway;
    private final LedgerService ledgerService;

    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          TicketService ticketService,
                          PaymentGateway paymentGateway,
                          LedgerService ledgerService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.ticketService = ticketService;
        this.paymentGateway = paymentGateway;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public PaymentDetailsDTO paySimulated(UUID orderId, User authenticated) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));

        if (!order.getUser().equals(authenticated)) {
            throw new OrderNotFoundException("Order not found");
        }
        if (order.belongsToCancelledEvent()) {
            throw new InvalidEventStateException("The event for this order was cancelled");
        }
        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.APPROVED)) {
            throw new InvalidPaymentStateException("This order has already been paid");
        }
        if (order.isExpired()) {
            throw new InvalidOrderStateException("This order has expired");
        }

        var payment = new Payment(order, PaymentMethod.SIMULATED);
        payment.approve();
        order.markAsPaid();
        ledgerService.recordSale(order);
        ticketService.issueForOrder(order);

        var saved = paymentRepository.saveAndFlush(payment);
        return new PaymentDetailsDTO(saved);
    }

    @Transactional
    public void refundApproved(Order order) {
        var payment = paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.APPROVED);
        if (payment.isEmpty()) {
            return;
        }
        var approved = payment.get();
        var result = paymentGateway.refund(new PaymentGateway.RefundRequest(
                approved.getId(), approved.getProviderReference(), approved.getAmount(), approved.getId().toString()));
        approved.refund(result.reference());
    }
}
