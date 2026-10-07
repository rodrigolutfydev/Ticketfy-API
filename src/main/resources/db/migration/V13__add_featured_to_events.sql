ALTER TABLE events
    ADD COLUMN featured BOOLEAN NOT NULL DEFAULT false;

-- Only featured active events are indexed, ordered like the public listing
CREATE INDEX idx_events_featured_starts_at ON events (starts_at) WHERE featured AND active;
