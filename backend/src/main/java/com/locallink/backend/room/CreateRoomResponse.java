package com.locallink.backend.room;

import java.time.Instant;
import java.util.UUID;

public record CreateRoomResponse(
        UUID roomId,
        String code,
        String pin,
        Instant expiresAt,
        UUID deviceId,
        String sessionToken,
        String role,
        String hostRecoveryCredential
) {}