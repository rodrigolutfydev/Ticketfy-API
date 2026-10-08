package com.lutfy.ticketfy.privacy;

import com.lutfy.ticketfy.payout.account.DocumentType;
import com.lutfy.ticketfy.payout.account.PixKeyType;
import com.lutfy.ticketfy.payout.ledger.BalanceDTO;
import com.lutfy.ticketfy.payout.ledger.LedgerEntryDTO;
import com.lutfy.ticketfy.user.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataExportDTO(
        int schemaVersion,
        Instant generatedAt,
        Profile profile,
        List<OrderEntry> orders,
        List<TicketEntry> tickets,
        List<TransferEntry> transfers,
        Organizer organizer
) {
    public static final int SCHEMA_VERSION = 1;

    public record Profile(UUID id, String name, String email, Role role, String avatarUrl,
                          Instant createdAt, Instant updatedAt) {}

    public record EventRef(UUID id, String name, Instant startsAt) {}

    public record OrderEntry(UUID id, String status, BigDecimal subtotalAmount, BigDecimal discountAmount,
                             BigDecimal totalAmount, String couponCode, Instant createdAt, Instant expiresAt,
                             EventRef event, List<OrderItemEntry> items, List<PaymentEntry> payments) {}

    public record OrderItemEntry(String ticketTypeName, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {}

    public record PaymentEntry(String method, String status, BigDecimal amount, Instant createdAt,
                               Instant approvedAt) {}

    public record TicketEntry(UUID id, String code, String status, EventRef event, String ticketTypeName,
                              int transferCount, Instant usedAt, Instant createdAt) {}

    public record TransferEntry(UUID ticketId, String direction, EventRef event, String ticketTypeName,
                                Instant transferredAt) {}

    public record Organizer(List<EventEntry> events, BalanceDTO balance, List<LedgerEntryDTO> ledger,
                            List<PayoutEntry> payouts, PayoutAccountEntry payoutAccount) {}

    public record EventEntry(UUID id, String name, String description, String imageUrl, String venueName,
                             String address, String city, String state, Instant startsAt, Instant endsAt,
                             boolean active, boolean featured, Instant cancelledAt, String cancellationReason,
                             Instant createdAt, List<TicketTypeEntry> ticketTypes, List<CouponEntry> coupons,
                             SalesSummary sales) {}

    public record TicketTypeEntry(UUID id, String name, String description, BigDecimal price, int quantityTotal,
                                  int quantitySold, Integer maxPerOrder, boolean active) {}

    public record CouponEntry(String code, String discountType, BigDecimal discountValue, Integer maxUses,
                              int usesCount, Instant startsAt, Instant endsAt, boolean active) {}

    public record SalesSummary(long paidOrders, BigDecimal paidAmount) {}

    public record PayoutEntry(UUID id, BigDecimal amount, String status, DocumentType documentType, String document,
                              String holderName, PixKeyType pixKeyType, String pixKey, Instant requestedAt,
                              Instant processingStartedAt, Instant finishedAt, String failureReason,
                              String rejectionReason) {}

    public record PayoutAccountEntry(DocumentType documentType, String document, String holderName,
                                     PixKeyType pixKeyType, String pixKey, Instant createdAt, Instant updatedAt) {}
}
