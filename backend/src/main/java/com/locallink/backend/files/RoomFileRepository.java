package com.locallink.backend.files;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomFileRepository extends JpaRepository<RoomFile, UUID> {

    List<RoomFile> findByRoomIdOrderByCreatedAtDesc(
            UUID roomId, Pageable pageable);

    Optional<RoomFile> findByIdAndRoomId(UUID id, UUID roomId);

    Optional<RoomFile> findByRoomIdAndUploaderDeviceIdAndClientUploadId(
            UUID roomId, UUID uploaderDeviceId, UUID clientUploadId);

    List<RoomFile> findByRoomId(UUID roomId);

    List<RoomFile> findByRoomIdIn(List<UUID> roomIds);

    void deleteByRoomIdIn(List<UUID> roomIds);

    long deleteByRoomId(UUID roomId);
}
