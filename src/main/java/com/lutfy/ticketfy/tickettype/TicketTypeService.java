package com.lutfy.ticketfy.tickettype;

import com.lutfy.ticketfy.event.Event;
import com.lutfy.ticketfy.user.User;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class TicketTypeService {

    private final TicketTypeCreationData ticketTypeCreationData;

    private final TicketTypeRepository repository;

    public TicketTypeService(TicketTypeCreationData ticketTypeCreationData, TicketTypeRepository repository) {
        this.ticketTypeCreationData = ticketTypeCreationData;
        this.repository = repository;
    }

    public TicketTypeDetails create(UUID eventId, TicketTypeCreationData data, User authenticated) {

    }


    public List<TicketTypeSummary> listByEvent(UUID eventId) {

    }

    private void checkEventOwnership(Event event, User authenticated) {

    }
}
