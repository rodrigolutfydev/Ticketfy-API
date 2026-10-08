package com.lutfy.ticketfy.payout.account;

public record PayoutDestination(
        DocumentType documentType,
        String document,
        String holderName,
        PixKeyType pixKeyType,
        String pixKey
) {}
