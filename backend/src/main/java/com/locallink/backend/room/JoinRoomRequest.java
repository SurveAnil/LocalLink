
package com.locallink.backend.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(

        @NotBlank(message = "Room code is required.")
        @Size(max = 20, message = "Room code is too long.")
        String code,

        @NotBlank(message = "PIN is required.")
        @Pattern(
                regexp = "\\d{6}",
                message = "PIN must contain exactly 6 digits."
        )
        String pin,

        @NotBlank(message = "Device name is required.")
        @Size(max = 100, message = "Device name must not exceed 100 characters.")
        String deviceName,

        @NotBlank(message = "Platform is required.")
        @Size(max = 50, message = "Platform must not exceed 50 characters.")
        String platform

) {}
