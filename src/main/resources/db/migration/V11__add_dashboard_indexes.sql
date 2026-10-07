-- Dashboard aggregations join items and tickets by ticket type
CREATE INDEX idx_order_items_ticket_type_id ON order_items (ticket_type_id);
CREATE INDEX idx_tickets_ticket_type_id ON tickets (ticket_type_id);
