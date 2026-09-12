CREATE TABLE events (
                        id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                        name         VARCHAR(150) NOT NULL,
                        description  TEXT,
                        venue_name   VARCHAR(150) NOT NULL,
                        address      VARCHAR(255) NOT NULL,
                        city         VARCHAR(100) NOT NULL,
                        state        CHAR(2)      NOT NULL,
                        starts_at    TIMESTAMP    NOT NULL,
                        ends_at      TIMESTAMP    NOT NULL,
                        organizer_id UUID         NOT NULL REFERENCES users(id),
                        active       BOOLEAN      NOT NULL DEFAULT TRUE,
                        created_at   TIMESTAMP    NOT NULL DEFAULT now(),
                        updated_at   TIMESTAMP
);

CREATE INDEX idx_events_city ON events (city);
CREATE INDEX idx_events_starts_at ON events (starts_at);
CREATE INDEX idx_events_organizer_id ON events (organizer_id);