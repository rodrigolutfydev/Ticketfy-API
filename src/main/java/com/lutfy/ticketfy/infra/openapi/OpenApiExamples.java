package com.lutfy.ticketfy.infra.openapi;

/** Exemplos de requisição e resposta das rotas principais, mostrados no Swagger UI. */
public final class OpenApiExamples {

    public static final String REGISTER_REQUEST = """
            {
              "name": "Ana Souza",
              "email": "ana@example.com",
              "password": "senha-forte-123",
              "language": "pt-BR"
            }""";

    public static final String REGISTER_RESPONSE = """
            {
              "id": "6b1f0c2e-8a4d-4f3b-9c5e-1a2b3c4d5e6f",
              "name": "Ana Souza",
              "email": "ana@example.com",
              "role": "USER",
              "avatarUrl": null,
              "language": "pt-BR",
              "hasPassword": true,
              "googleLinked": false
            }""";

    public static final String LOGIN_REQUEST = """
            {
              "email": "ana@example.com",
              "password": "senha-forte-123"
            }""";

    public static final String LOGIN_RESPONSE = """
            {
              "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbmFAZXhhbXBsZS5jb20ifQ.c2lnbmF0dXJl",
              "expiresIn": 600
            }""";

    public static final String EVENT_REQUEST = """
            {
              "name": "Festival de Jazz da Lapa",
              "description": "Três palcos e doze atrações ao longo da noite.",
              "imageUrl": "https://images.example.com/jazz-lapa.jpg",
              "venueName": "Fundição Progresso",
              "address": "Rua dos Arcos, 24 - Lapa",
              "city": "Rio de Janeiro",
              "state": "RJ",
              "startsAt": "2026-12-10T23:00:00Z",
              "endsAt": "2026-12-11T04:00:00Z",
              "category": "MUSIC"
            }""";

    public static final String EVENT_RESPONSE = """
            {
              "id": "3f2b8c1e-7d4a-4e9b-a1c2-5d6e7f8a9b0c",
              "name": "Festival de Jazz da Lapa",
              "description": "Três palcos e doze atrações ao longo da noite.",
              "imageUrl": "https://images.example.com/jazz-lapa.jpg",
              "venueName": "Fundição Progresso",
              "address": "Rua dos Arcos, 24 - Lapa",
              "city": "Rio de Janeiro",
              "state": "RJ",
              "startsAt": "2026-12-10T23:00:00Z",
              "endsAt": "2026-12-11T04:00:00Z",
              "organizerName": "Ana Souza",
              "organizerId": "6b1f0c2e-8a4d-4f3b-9c5e-1a2b3c4d5e6f",
              "createdAt": "2026-10-16T14:30:00Z",
              "featured": false,
              "cancelledAt": null,
              "cancellationReason": null,
              "salesClosed": false,
              "category": "MUSIC"
            }""";

    public static final String ORDER_REQUEST = """
            {
              "items": [
                { "ticketTypeId": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", "quantity": 2 }
              ],
              "couponCode": "JAZZ10"
            }""";

    public static final String ORDER_RESPONSE = """
            {
              "id": "c4d5e6f7-a8b9-4c0d-9e1f-2a3b4c5d6e7f",
              "status": "PENDING",
              "subtotalAmount": 160.00,
              "discountAmount": 16.00,
              "totalAmount": 144.00,
              "couponCode": "JAZZ10",
              "expiresAt": "2026-10-16T14:45:00Z",
              "createdAt": "2026-10-16T14:30:00Z",
              "items": [
                {
                  "ticketTypeId": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
                  "ticketTypeName": "Pista - 1º lote",
                  "unitPrice": 80.00,
                  "quantity": 2,
                  "subtotal": 160.00
                }
              ],
              "tickets": [],
              "eventCancelled": false,
              "eventId": "3f2b8c1e-7d4a-4e9b-a1c2-5d6e7f8a9b0c",
              "eventName": "Festival de Jazz da Lapa",
              "eventStartsAt": "2026-12-10T23:00:00Z"
            }""";

    private OpenApiExamples() {
    }
}
