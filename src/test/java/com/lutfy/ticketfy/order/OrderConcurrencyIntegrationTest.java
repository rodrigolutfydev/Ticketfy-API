package com.lutfy.ticketfy.order;

import com.lutfy.ticketfy.IntegrationTestBase;
import com.lutfy.ticketfy.infra.exception.InsufficientStockException;
import com.lutfy.ticketfy.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void onlyOneOfTenConcurrentBuyersGetsTheLastTicket() throws Exception {
        var organizerId = insertUser("ORGANIZER");
        var buyerId = insertUser("USER");
        var eventId = insertEvent(organizerId);
        var ticketTypeId = insertTicketType(eventId, 1);

        var buyer = userRepository.findById(buyerId).orElseThrow();
        var dto = new OrderCreationDTO(List.of(new OrderItemRequestDTO(ticketTypeId, 1)));

        int threads = 10;
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);

        var successes = new AtomicInteger();
        var outOfStock = new AtomicInteger();
        var unexpectedErrors = new ConcurrentLinkedQueue<Throwable>();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    orderService.create(dto, null, buyer);
                    successes.incrementAndGet();
                } catch (InsufficientStockException ex) {
                    outOfStock.incrementAndGet();
                } catch (Throwable ex) {
                    unexpectedErrors.add(ex);
                }
                return null;
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(unexpectedErrors).isEmpty();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(outOfStock.get()).isEqualTo(9);

        var sold = jdbc.queryForObject(
                "SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, ticketTypeId);
        assertThat(sold).isEqualTo(1);

        var orders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE ticket_type_id = ?", Integer.class, ticketTypeId);
        assertThat(orders).isEqualTo(1);
    }
}
