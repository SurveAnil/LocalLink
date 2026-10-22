ALTER TABLE room_file
    ADD COLUMN client_upload_id UUID;

CREATE UNIQUE INDEX idx_room_file_upload_idempotency
    ON room_file(room_id, uploader_device_id, client_upload_id);
