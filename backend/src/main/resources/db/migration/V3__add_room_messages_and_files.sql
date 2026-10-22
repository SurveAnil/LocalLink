CREATE TABLE chat_message (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    sender_device_id UUID NOT NULL,
    sender_name VARCHAR(100) NOT NULL,
    idempotency_key UUID NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_chat_message_room
        FOREIGN KEY (room_id) REFERENCES room(id),
    CONSTRAINT fk_chat_message_sender
        FOREIGN KEY (sender_device_id) REFERENCES device(id)
);

CREATE INDEX idx_chat_message_room_created
    ON chat_message(room_id, created_at DESC);
CREATE UNIQUE INDEX idx_chat_message_idempotency
    ON chat_message(room_id, sender_device_id, idempotency_key);

CREATE TABLE room_file (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    uploader_device_id UUID NOT NULL,
    uploader_name VARCHAR(100) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    storage_key VARCHAR(80) NOT NULL UNIQUE,
    CONSTRAINT chk_room_file_size CHECK (size_bytes > 0),
    CONSTRAINT fk_room_file_room
        FOREIGN KEY (room_id) REFERENCES room(id),
    CONSTRAINT fk_room_file_uploader
        FOREIGN KEY (uploader_device_id) REFERENCES device(id)
);

CREATE INDEX idx_room_file_room_created
    ON room_file(room_id, created_at DESC);
