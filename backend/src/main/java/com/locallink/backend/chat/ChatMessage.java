package com.locallink.backend.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "sender_device_id", nullable = false)
    private UUID senderDeviceId;

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {}

    public ChatMessage(
            UUID id,
            UUID roomId,
            UUID senderDeviceId,
            String senderName,
            UUID idempotencyKey,
            String content,
            Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.senderDeviceId = senderDeviceId;
        this.senderName = senderName;
        this.idempotencyKey = idempotencyKey;
        this.content = content;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public UUID getSenderDeviceId() { return senderDeviceId; }
    public String getSenderName() { return senderName; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
