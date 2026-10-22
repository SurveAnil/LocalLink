package com.locallink.backend.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.locallink.backend.device.Device;
import com.locallink.backend.room.Room;
import com.locallink.backend.room.RoomRepository;
import com.locallink.backend.room.RoomService;
import com.locallink.backend.realtime.RoomRealtimePublisher;

@Service
public class ChatMessageService {

    private static final int MAX_HISTORY = 100;
    private final ChatMessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final RoomRealtimePublisher realtimePublisher;

    public ChatMessageService(
            ChatMessageRepository messageRepository,
            RoomRepository roomRepository,
            RoomRealtimePublisher realtimePublisher) {
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> list(UUID roomId) {
        ensureRoomActive(roomId);
        return messageRepository.findByRoomIdOrderByCreatedAtDesc(
                        roomId, PageRequest.of(0, MAX_HISTORY))
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional
    public ChatMessageResponse send(
            UUID roomId, Device sender, ChatMessageRequest request) {
        ensureRoomActive(roomId);
        String content = request.content().trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Message content is required.");
        }
        var existing = messageRepository
                .findByRoomIdAndSenderDeviceIdAndIdempotencyKey(
                        roomId, sender.getId(), request.clientMessageId());
        if (existing.isPresent()) {
            return ChatMessageResponse.from(existing.get());
        }
        ChatMessage message = new ChatMessage(
                UUID.randomUUID(),
                roomId,
                sender.getId(),
                sender.getName(),
                request.clientMessageId(),
                content,
                Instant.now());
        ChatMessageResponse response =
                ChatMessageResponse.from(messageRepository.save(message));
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        realtimePublisher.publishMessage(response);
                    }
                });
        return response;
    }

    private Room ensureRoomActive(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(RoomService.RoomNotFoundException::new);
        if (!room.getExpiresAt().isAfter(Instant.now())
                || room.getStatus() == Room.RoomStatus.EXPIRED) {
            throw new RoomService.RoomExpiredException(room.getExpiresAt());
        }
        if (room.getStatus() != Room.RoomStatus.ACTIVE) {
            throw new RoomService.RoomClosedException();
        }
        return room;
    }
}
