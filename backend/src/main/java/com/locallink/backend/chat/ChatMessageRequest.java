package com.locallink.backend.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChatMessageRequest(
        @NotNull UUID clientMessageId,
        @NotBlank @Size(max = 4000) String content
) {}
