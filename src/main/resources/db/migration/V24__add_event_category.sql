ALTER TABLE events
    ADD COLUMN category VARCHAR(30) NOT NULL DEFAULT 'OTHER';

ALTER TABLE events
    ADD CONSTRAINT ck_events_category
        CHECK (category IN ('MUSIC', 'THEATER', 'COMEDY', 'PARTIES', 'SPORTS', 'COURSES', 'TALKS', 'ARTS', 'FOOD',
                            'KIDS', 'OTHER'));

CREATE INDEX idx_events_category ON events (category);
