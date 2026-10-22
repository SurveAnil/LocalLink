package com.locallink.backend.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 100)
        String roomName,

        @NotBlank @Size(max = 100)
        String deviceName,

        @NotBlank @Size(max = 50)
        String platform,

        @NotNull
        Boolean allowFileSharing,

        @Min(2) @Max(12)
        Integer maxMembers,

        @Min(5) @Max(1440)
        Integer ttlMinutes,

        @NotNull
        Boolean quickJoin,

        @NotNull @Pattern(regexp = "\\d{4}",
                message = "Host recovery PIN must contain exactly four digits.")
        String hostRecoveryPin,

        String publicKey
) {}