package com.locallink.backend.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.PathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.locallink.backend.device.Device;
import com.locallink.backend.device.DeviceRepository;
import com.locallink.backend.room.Room;
import com.locallink.backend.room.RoomRepository;
import com.locallink.backend.room.RoomService;
import com.locallink.backend.realtime.RoomRealtimePublisher;

@Service
public class RoomFileService {

    private static final int MAX_HISTORY = 100;

    private final RoomFileRepository fileRepository;
    private final RoomRepository roomRepository;
    private final DeviceRepository deviceRepository;
    private final FileStorage fileStorage;
    private final long maxFileSizeBytes;
    private final RoomRealtimePublisher realtimePublisher;

    public RoomFileService(
            RoomFileRepository fileRepository,
            RoomRepository roomRepository,
            DeviceRepository deviceRepository,
            FileStorage fileStorage,
            RoomRealtimePublisher realtimePublisher,
            @Value("${locallink.files.max-size-bytes:2147483648}")
            long maxFileSizeBytes) {
        this.fileRepository = fileRepository;
        this.roomRepository = roomRepository;
        this.deviceRepository = deviceRepository;
        this.fileStorage = fileStorage;
        this.realtimePublisher = realtimePublisher;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional(readOnly = true)
    public List<RoomFileResponse> list(UUID roomId) {
        return list(roomId, 0, MAX_HISTORY);
    }

    @Transactional(readOnly = true)
    public List<RoomFileResponse> list(UUID roomId, int page, int size) {
        ensureRoomActive(roomId);
        if (page < 0 || size < 1 || size > MAX_HISTORY) {
            throw new IllegalArgumentException(
                    "File page must be nonnegative and page size must be between 1 and 100.");
        }
        return fileRepository.findByRoomIdOrderByCreatedAtDesc(
                        roomId, PageRequest.of(page, size))
                .stream()
                .map(RoomFileResponse::from)
                .toList();
    }

    @Transactional
    public RoomFileResponse upload(
            UUID roomId, Device uploader, MultipartFile upload) {
        return upload(roomId, uploader, upload, null);
    }

    @Transactional
    public RoomFileResponse upload(
            UUID roomId,
            Device uploader,
            MultipartFile upload,
            UUID clientUploadId) {
        Room room = ensureRoomActive(roomId);
        if (!room.isAllowFileSharing()) {
            throw new FileSharingDisabledException();
        }
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }
        if (upload.getSize() > maxFileSizeBytes) {
            throw new FileTooLargeException(maxFileSizeBytes);
        }

        String originalFilename = sanitizeFilename(upload.getOriginalFilename());
        String contentType = sanitizeContentType(upload.getContentType());
        Device actualUploader = deviceRepository.findById(uploader.getId())
                .filter(device -> device.getRoomId().equals(roomId))
                .orElseThrow(RoomService.UnauthorizedRoomAccessException::new);
        if (clientUploadId != null) {
            var existing = fileRepository
                    .findByRoomIdAndUploaderDeviceIdAndClientUploadId(
                            roomId, actualUploader.getId(), clientUploadId);
            if (existing.isPresent()) {
                RoomFile prior = existing.get();
                if (prior.getSizeBytes() != upload.getSize()
                        || !prior.getOriginalFilename()
                                .equals(originalFilename)) {
                    throw new FileUploadConflictException();
                }
                return RoomFileResponse.from(prior);
            }
        }
        UUID fileId = UUID.randomUUID();
        String storageKey;
        try (InputStream input = upload.getInputStream()) {
            storageKey = fileStorage.store(roomId, fileId, input);
        } catch (IOException ex) {
            throw new FileStorageException("Unable to store uploaded file.", ex);
        }

        RoomFile file = new RoomFile(
                fileId,
                roomId,
                actualUploader.getId(),
                actualUploader.getName(),
                originalFilename,
                contentType,
                upload.getSize(),
                Instant.now(),
                storageKey,
                clientUploadId);
        try {
            ensureRoomActive(roomId);
            RoomFileResponse response =
                    RoomFileResponse.from(fileRepository.save(file));
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            realtimePublisher.publishFile(response);
                        }
                    });
            return response;
        } catch (RuntimeException ex) {
            try {
                fileStorage.delete(storageKey);
            } catch (IOException cleanupError) {
                ex.addSuppressed(cleanupError);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public DownloadableFile download(UUID roomId, UUID fileId) {
        ensureRoomActive(roomId);
        RoomFile file = fileRepository.findByIdAndRoomId(fileId, roomId)
                .orElseThrow(FileNotFoundException::new);
        Path path = fileStorage.resolve(file.getStorageKey());
        if (!Files.isRegularFile(path)) {
            throw new FileNotFoundException();
        }
        Resource resource = new PathResource(path);
        return new DownloadableFile(file, resource);
    }

    @Scheduled(cron = "${locallink.files.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void cleanupExpiredRoomFiles() {
        List<Room> expiredRooms =
                roomRepository.findByStatusOrExpiresAtLessThanEqual(
                        Room.RoomStatus.EXPIRED, Instant.now());
        if (expiredRooms.isEmpty()) {
            return;
        }

        List<UUID> roomIds = expiredRooms.stream().map(Room::getId).toList();
        List<RoomFile> files = fileRepository.findByRoomIdIn(roomIds);
        for (RoomFile file : files) {
            try {
                fileStorage.delete(file.getStorageKey());
            } catch (IOException ex) {
                throw new FileStorageException(
                        "Unable to clean up expired room files.", ex);
            }
        }
        fileRepository.deleteByRoomIdIn(roomIds);
    }

    private Room ensureRoomActive(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(RoomService.RoomNotFoundException::new);
        if (!room.getExpiresAt().isAfter(Instant.now())
                || room.getStatus() == Room.RoomStatus.EXPIRED) {
            throw new RoomService.RoomExpiredException(room.getExpiresAt());
        }
        if (room.getStatus() != Room.RoomStatus.ACTIVE) {
            throw new RoomService.RoomClosedException();
        }
        return room;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "download";
        }
        String sanitized = filename.replace('\\', '/');
        sanitized = sanitized.substring(sanitized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim();
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            return "download";
        }
        return sanitized.length() > 255
                ? sanitized.substring(sanitized.length() - 255)
                : sanitized;
    }

    private String sanitizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()
                || contentType.indexOf('\r') >= 0
                || contentType.indexOf('\n') >= 0) {
            return "application/octet-stream";
        }
        return contentType.length() > 255
                ? "application/octet-stream" : contentType;
    }

    public record DownloadableFile(RoomFile file, Resource resource) {}

    public static class FileTooLargeException extends RuntimeException {
        private final long maxSizeBytes;
        public FileTooLargeException(long maxSizeBytes) {
            super("The file exceeds the configured upload limit.");
            this.maxSizeBytes = maxSizeBytes;
        }
        public long getMaxSizeBytes() { return maxSizeBytes; }
    }

    public static class FileSharingDisabledException extends RuntimeException {
        public FileSharingDisabledException() {
            super("File sharing is disabled for this room.");
        }
    }

    public static class FileNotFoundException extends RuntimeException {
        public FileNotFoundException() { super("ROOM_FILE_NOT_FOUND"); }
    }

    public static class FileStorageException extends RuntimeException {
        public FileStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class FileUploadConflictException extends RuntimeException {
        public FileUploadConflictException() {
            super("This upload ID was already used for a different file.");
        }
    }
}
