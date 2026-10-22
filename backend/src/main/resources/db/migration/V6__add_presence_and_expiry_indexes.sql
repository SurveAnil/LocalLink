CREATE INDEX idx_device_room_status_last_seen
    ON device(room_id, status, last_seen_at);

CREATE INDEX idx_room_status_expires
    ON room(status, expires_at);
