package com.locallink.backend.files;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_file")
public class RoomFile {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "uploader_device_id", nullable = false)
    private UUID uploaderDeviceId;

    @Column(name = "uploader_name", nullable = false, length = 100)
    private String uploaderName;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "storage_key", nullable = false, unique = true, length = 80)
    private String storageKey;

    @Column(name = "client_upload_id")
    private UUID clientUploadId;

    protected RoomFile() {}

    public RoomFile(
            UUID id,
            UUID roomId,
            UUID uploaderDeviceId,
            String uploaderName,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Instant createdAt,
            String storageKey,
            UUID clientUploadId) {
        this.id = id;
        this.roomId = roomId;
        this.uploaderDeviceId = uploaderDeviceId;
        this.uploaderName = uploaderName;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
        this.storageKey = storageKey;
        this.clientUploadId = clientUploadId;
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public UUID getUploaderDeviceId() { return uploaderDeviceId; }
    public String getUploaderName() { return uploaderName; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStorageKey() { return storageKey; }
    public UUID getClientUploadId() { return clientUploadId; }
}
