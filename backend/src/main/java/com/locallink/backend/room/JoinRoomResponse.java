package com.locallink.backend.room;

import java.time.Instant;
import java.util.UUID;

public record JoinRoomResponse(
    UUID roomId,
    String code,
    UUID deviceId,
    String sessionToken,
    String role,
    Instant expiresAt
) {}