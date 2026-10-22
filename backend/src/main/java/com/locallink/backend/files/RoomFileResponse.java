package com.locallink.backend.files;

import java.time.Instant;
import java.util.UUID;

public record RoomFileResponse(
        UUID id,
        UUID roomId,
        UUID uploaderDeviceId,
        String uploaderName,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {
    public static RoomFileResponse from(RoomFile file) {
        return new RoomFileResponse(
                file.getId(),
                file.getRoomId(),
                file.getUploaderDeviceId(),
                file.getUploaderName(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getCreatedAt());
    }
}
