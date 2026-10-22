package com.locallink.backend.realtime;

import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import jakarta.validation.Valid;

import com.locallink.backend.chat.ChatMessageRequest;
import com.locallink.backend.chat.ChatMessageService;
import com.locallink.backend.device.Device;
import com.locallink.backend.device.DeviceRepository;
import com.locallink.backend.room.RoomService;

import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

@Controller
public class RoomRealtimeController {

    private final ChatMessageService chatMessageService;
    private final DeviceRepository deviceRepository;

    public RoomRealtimeController(
            ChatMessageService chatMessageService,
            DeviceRepository deviceRepository) {
        this.chatMessageService = chatMessageService;
        this.deviceRepository = deviceRepository;
    }

    @MessageMapping("/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable UUID roomId,
            @Valid @Payload ChatMessageRequest request,
            SimpMessageHeaderAccessor headers) {
        Object authenticatedRoomId = headers.getSessionAttributes()
                .get(RoomStompAuthorizationInterceptor.roomIdAttribute());
        Object authenticatedDeviceId = headers.getSessionAttributes()
                .get(RoomStompAuthorizationInterceptor.deviceIdAttribute());
        if (!roomId.toString().equals(authenticatedRoomId)
                || !(authenticatedDeviceId instanceof String deviceId)) {
            throw new RoomService.UnauthorizedRoomAccessException();
        }
        Device sender = deviceRepository.findById(UUID.fromString(deviceId))
                .filter(device -> device.getRoomId().equals(roomId))
                .orElseThrow(RoomService.UnauthorizedRoomAccessException::new);
        chatMessageService.send(roomId, sender, request);
    }
}
