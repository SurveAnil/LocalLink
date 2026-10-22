package com.locallink.backend.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RecoverHostPinRequest(
        @NotBlank String code,
        @NotBlank @Pattern(regexp = "\\d{6}") String roomPin,
        @NotBlank @Pattern(regexp = "\\d{4}") String hostRecoveryPin
) {}
