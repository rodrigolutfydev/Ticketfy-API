package com.lutfy.ticketfy.infra.mail;

public record EmailAttachment(String fileName, String contentType, byte[] content) {
}
