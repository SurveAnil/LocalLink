package com.locallink.backend.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID roomId,
        UUID senderDeviceId,
        String senderName,
        String content,
        Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getSenderDeviceId(),
                message.getSenderName(),
                message.getContent(),
                message.getCreatedAt());
    }
}
