
package com.locallink.backend.device;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "device")
public class Device {

    public enum DeviceRole {
        HOST,
        GUEST
    }

    public enum DeviceStatus {
        ACTIVE,
        DISCONNECTED,
        REMOVED
    }

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceRole role;

    @Column(name = "public_key_jwk", columnDefinition = "text")
    private String publicKeyJwk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status = DeviceStatus.ACTIVE;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public Device() {
        // Required by JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public DeviceRole getRole() {
        return role;
    }

    public void setRole(DeviceRole role) {
        this.role = role;
    }

    public String getPublicKeyJwk() {
        return publicKeyJwk;
    }

    public void setPublicKeyJwk(String publicKeyJwk) {
        this.publicKeyJwk = publicKeyJwk;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceStatus status) {
        this.status = status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
