package com.locallink.backend.realtime;

import java.util.UUID;

public record RoomMemberUpdate(
        UUID roomId,
        long memberCount,
        boolean closed) {
    public RoomMemberUpdate(UUID roomId, long memberCount) {
        this(roomId, memberCount, false);
    }
}
