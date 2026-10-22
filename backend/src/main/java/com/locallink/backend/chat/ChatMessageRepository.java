package com.locallink.backend.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByRoomIdOrderByCreatedAtDesc(
            UUID roomId, Pageable pageable);

    java.util.Optional<ChatMessage>
            findByRoomIdAndSenderDeviceIdAndIdempotencyKey(
                    UUID roomId, UUID senderDeviceId, UUID idempotencyKey);
}
