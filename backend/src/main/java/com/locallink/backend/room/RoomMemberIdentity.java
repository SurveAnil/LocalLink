package com.locallink.backend.room;

import java.util.UUID;

public record RoomMemberIdentity(
        UUID deviceId,
        String name,
        String role
) {}
