package com.locallink.backend.chat;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.locallink.backend.device.Device;
import com.locallink.backend.room.RoomService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/messages")
public class ChatController {

    private final ChatMessageService messageService;

    public ChatController(ChatMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public List<ChatMessageResponse> list(
            @PathVariable UUID roomId,
            HttpServletRequest request) {
        requireRoomMember(roomId, request);
        return messageService.list(roomId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse send(
            @PathVariable UUID roomId,
            @Valid @RequestBody ChatMessageRequest body,
            HttpServletRequest request) {
        return messageService.send(
                roomId, requireRoomMember(roomId, request), body);
    }

    private Device requireRoomMember(
            UUID roomId, HttpServletRequest request) {
        Device device = (Device) request.getAttribute("authenticatedDevice");
        if (device == null) {
            throw new RoomService.UnauthorizedRoomAccessException();
        }
        if (!device.getRoomId().equals(roomId)) {
            throw new RoomService.UnauthorizedRoomAccessException();
        }
        return device;
    }
}
