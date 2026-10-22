
package com.locallink.backend.room;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "room")
public class Room {

    public enum RoomStatus {
        ACTIVE,
        CLOSED,
        EXPIRED,
        LOCKED
    }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    @Column(name = "room_name", nullable = false, length = 100)
    private String roomName;

    @Column(name = "pin_hash", nullable = false)
    private String pinHash;

    @Column(name = "pin_salt", nullable = false)
    private String pinSalt;

    @Column(name = "allow_file_sharing", nullable = false)
    private boolean allowFileSharing = true;

    @Column(name = "max_members", nullable = false)
    private int maxMembers = 8;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status = RoomStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "quick_join", nullable = false)
    private boolean quickJoin = false;

    @Column(name = "host_device_id")
    private UUID hostDeviceId;

    @Column(name = "host_recovery_hash")
    private String hostRecoveryHash;

    @Column(name = "host_recovery_pin_hash")
    private String hostRecoveryPinHash;

    @Column(name = "host_recovery_pin_failed_attempts", nullable = false)
    private int hostRecoveryPinFailedAttempts;

    @Column(name = "host_recovery_pin_locked_until")
    private Instant hostRecoveryPinLockedUntil;

    protected Room() {
        // Required by JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public String getPinSalt() {
        return pinSalt;
    }

    public void setPinSalt(String pinSalt) {
        this.pinSalt = pinSalt;
    }

    public boolean isAllowFileSharing() {
        return allowFileSharing;
    }

    public void setAllowFileSharing(boolean allowFileSharing) {
        this.allowFileSharing = allowFileSharing;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public boolean isQuickJoin() {
        return quickJoin;
    }

    public void setQuickJoin(boolean quickJoin) {
        this.quickJoin = quickJoin;
    }

    public UUID getHostDeviceId() {
        return hostDeviceId;
    }

    public void setHostDeviceId(UUID hostDeviceId) {
        this.hostDeviceId = hostDeviceId;
    }

    public String getHostRecoveryHash() {
        return hostRecoveryHash;
    }

    public void setHostRecoveryHash(String hostRecoveryHash) {
        this.hostRecoveryHash = hostRecoveryHash;
    }

    public String getHostRecoveryPinHash() {
        return hostRecoveryPinHash;
    }

    public void setHostRecoveryPinHash(String hostRecoveryPinHash) {
        this.hostRecoveryPinHash = hostRecoveryPinHash;
    }

    public int getHostRecoveryPinFailedAttempts() {
        return hostRecoveryPinFailedAttempts;
    }

    public void setHostRecoveryPinFailedAttempts(int attempts) {
        this.hostRecoveryPinFailedAttempts = attempts;
    }

    public Instant getHostRecoveryPinLockedUntil() {
        return hostRecoveryPinLockedUntil;
    }

    public void setHostRecoveryPinLockedUntil(Instant lockedUntil) {
        this.hostRecoveryPinLockedUntil = lockedUntil;
    }
}
