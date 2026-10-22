package com.locallink.backend.room;

import jakarta.validation.constraints.NotBlank;

public record RecoverHostRequest(
        @NotBlank String code,
        @NotBlank String recoveryCredential
) {}
