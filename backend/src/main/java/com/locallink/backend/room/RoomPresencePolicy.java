package com.locallink.backend.room;

import java.time.Instant;

public final class RoomPresencePolicy {

    private static final long MEMBER_IDLE_SECONDS = 120;

    private RoomPresencePolicy() {}

    public static Instant activeSince(Instant now) {
        return now.minusSeconds(MEMBER_IDLE_SECONDS);
    }
}
