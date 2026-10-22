package com.locallink.backend.realtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.locallink.backend.chat.ChatMessageResponse;
import com.locallink.backend.device.Device;
import com.locallink.backend.device.DeviceRepository;
import com.locallink.backend.files.RoomFileResponse;
import com.locallink.backend.room.Room;
import com.locallink.backend.room.RoomRepository;
import com.locallink.backend.room.RoomPresencePolicy;

@Component
public class RoomRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepository roomRepository;
    private final DeviceRepository deviceRepository;
    private final Map<UUID, Long> lastPublishedMemberCounts =
            new ConcurrentHashMap<>();

    public RoomRealtimePublisher(
            SimpMessagingTemplate messagingTemplate,
            RoomRepository roomRepository,
            DeviceRepository deviceRepository) {
        this.messagingTemplate = messagingTemplate;
        this.roomRepository = roomRepository;
        this.deviceRepository = deviceRepository;
    }

    public void publishMessage(ChatMessageResponse message) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + message.roomId() + "/messages", message);
    }

    public void publishFile(RoomFileResponse file) {
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + file.roomId() + "/files", file);
    }

    public void refreshMemberCount(UUID roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room != null && isActive(room)) {
            publishMemberCountIfChanged(room);
        }
    }

    public void publishRoomClosed(UUID roomId) {
        lastPublishedMemberCounts.remove(roomId);
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/members",
                new RoomMemberUpdate(roomId, 0, true));
    }

    @Scheduled(fixedDelayString = "${locallink.realtime.presence-check-ms:5000}")
    public void publishChangedMemberCounts() {
        List<Room> rooms = roomRepository.findAll();
        for (Room room : rooms) {
            if (isActive(room)) {
                publishMemberCountIfChanged(room);
            } else {
                lastPublishedMemberCounts.remove(room.getId());
            }
        }
    }

    private void publishMemberCountIfChanged(Room room) {
        Instant now = Instant.now();
        long memberCount =
                deviceRepository.countByRoomIdAndStatusAndLastSeenAtAfter(
                        room.getId(),
                        Device.DeviceStatus.ACTIVE,
                        RoomPresencePolicy.activeSince(now));
        Long previous = lastPublishedMemberCounts.put(room.getId(), memberCount);
        if (previous == null || previous != memberCount) {
            messagingTemplate.convertAndSend(
                    "/topic/rooms/" + room.getId() + "/members",
                    new RoomMemberUpdate(room.getId(), memberCount));
        }
    }

    private boolean isActive(Room room) {
        return room.getStatus() == Room.RoomStatus.ACTIVE
                && room.getExpiresAt().isAfter(Instant.now());
    }
}
