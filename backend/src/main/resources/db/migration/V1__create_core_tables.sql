
CREATE TABLE room (
    id UUID PRIMARY KEY,
    code VARCHAR(12) NOT NULL UNIQUE,
    room_name VARCHAR(100) NOT NULL,
    pin_hash VARCHAR(255) NOT NULL,
    pin_salt VARCHAR(255) NOT NULL,
    allow_file_sharing BOOLEAN NOT NULL DEFAULT TRUE,
    max_members INTEGER NOT NULL DEFAULT 8,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL,
    quick_join BOOLEAN NOT NULL DEFAULT FALSE,
    host_device_id UUID,
    CONSTRAINT chk_room_max_members
        CHECK (max_members BETWEEN 2 AND 12)
);

CREATE TABLE device (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    public_key_jwk TEXT,
    status VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_device_room
        FOREIGN KEY (room_id) REFERENCES room(id)
);

CREATE TABLE session_token (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_session_token_device
        FOREIGN KEY (device_id) REFERENCES device(id)
);

CREATE INDEX idx_device_room_id ON device(room_id);
CREATE INDEX idx_session_token_device_id ON session_token(device_id);

ALTER TABLE room
    ADD CONSTRAINT fk_room_host_device
    FOREIGN KEY (host_device_id) REFERENCES device(id);
