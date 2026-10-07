package com.lutfy.ticketfy.payout;

public record PayoutDestination(
        DocumentType documentType,
        String document,
        String holderName,
        PixKeyType pixKeyType,
        String pixKey
) {}
