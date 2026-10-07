package com.lutfy.ticketfy.payout;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulatedPayoutGateway implements PayoutGateway {

    private final Map<String, TransferResult> transfers = new ConcurrentHashMap<>();

    @Override
    public Optional<String> lookupPixKeyHolder(PixKeyLookup lookup) {
        return switch (lookup.type()) {
            case CPF, CNPJ -> Optional.of(lookup.key());
            case EMAIL, PHONE, RANDOM -> Optional.ofNullable(lookup.requesterDocument());
        };
    }

    @Override
    public TransferResult transfer(TransferRequest request) {
        return transfers.computeIfAbsent(request.idempotencyKey(), key ->
                new TransferResult(TransferStatus.COMPLETED, "SIM-PAYOUT-" + key, null));
    }

    @Override
    public Optional<TransferResult> findTransfer(String idempotencyKey) {
        return Optional.ofNullable(transfers.get(idempotencyKey));
    }
}
