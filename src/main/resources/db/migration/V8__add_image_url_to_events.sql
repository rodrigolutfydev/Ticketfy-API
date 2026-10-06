ALTER TABLE events
    ADD COLUMN image_url VARCHAR(500);

ALTER TABLE events
    ADD CONSTRAINT chk_events_image_url_https
        CHECK (image_url IS NULL OR image_url LIKE 'https://%');