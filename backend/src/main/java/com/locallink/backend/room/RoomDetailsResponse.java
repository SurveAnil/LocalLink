package com.locallink.backend.room;

import java.time.Instant;
import java.util.UUID;

public record RoomDetailsResponse(
        UUID roomId,
        String code,
        String roomName,
        boolean allowFileSharing,
        int maxMembers,
        Room.RoomStatus status,
        Instant createdAt,
        Instant expiresAt,
        long memberCount
) {}
