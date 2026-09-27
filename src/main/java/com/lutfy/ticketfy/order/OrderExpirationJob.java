package com.lutfy.ticketfy.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationJob.class);

    private final OrderService orderService;

    public OrderExpirationJob(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 60000)
    public void expireOverdueOrders() {
        int expired = orderService.expireOverdueOrders();
        if (expired > 0) {
            log.info("Expired {} overdue orders", expired);
        }
    }
}
