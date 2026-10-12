package com.lutfy.ticketfy.user.admin;

import com.lutfy.ticketfy.payout.admin.PayoutBlock;
import com.lutfy.ticketfy.user.Role;
import com.lutfy.ticketfy.user.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record AdminUserDTO(
        UUID id,
        String name,
        String email,
        Role role,
        Instant createdAt,
        boolean suspended,
        Instant suspendedAt,
        String suspensionReason,
        UUID suspendedBy,
        boolean payoutsBlocked,
        String payoutBlockReason,
        Instant payoutBlockedAt
) {
    public AdminUserDTO(User user, Optional<PayoutBlock> block) {
        this(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt(),
                user.isSuspended(), user.getSuspendedAt(), user.getSuspensionReason(), user.getSuspendedBy(),
                block.isPresent(), block.map(PayoutBlock::getReason).orElse(null),
                block.map(PayoutBlock::getBlockedAt).orElse(null));
    }
}
