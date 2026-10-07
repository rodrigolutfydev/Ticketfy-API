package com.lutfy.ticketfy.tickettype;

import java.math.BigDecimal;
import java.util.UUID;

public interface EventPricing {
    UUID getEventId();
    BigDecimal getMinPrice();
    Long getAvailableCount();
}
