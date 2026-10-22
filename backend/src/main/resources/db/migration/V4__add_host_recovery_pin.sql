ALTER TABLE room
    ADD COLUMN host_recovery_pin_hash VARCHAR(100);

ALTER TABLE room
    ADD COLUMN host_recovery_pin_failed_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE room
    ADD COLUMN host_recovery_pin_locked_until TIMESTAMP WITH TIME ZONE;
