
package com.locallink.backend.room;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;


import com.locallink.backend.device.Device;
import com.locallink.backend.device.DeviceRepository;
import com.locallink.backend.session.SessionToken;
import com.locallink.backend.session.SessionTokenRepository;
import com.locallink.backend.realtime.RoomRealtimePublisher;

@Service
public class RoomService {

    private static final long MEMBER_IDLE_SECONDS = 120;
    private static final int MAX_HOST_PIN_ATTEMPTS = 5;
    private static final long HOST_PIN_LOCK_MINUTES = 15;

    private final RoomRepository roomRepository;
    private final DeviceRepository deviceRepository;
    private final SessionTokenRepository sessionTokenRepository;
    private final RoomRealtimePublisher realtimePublisher;
    private final BCryptPasswordEncoder hostRecoveryPinEncoder =
            new BCryptPasswordEncoder(12);

    private final SecureRandom secureRandom = new SecureRandom();

    public RoomService(
            RoomRepository roomRepository,
            DeviceRepository deviceRepository,
            SessionTokenRepository sessionTokenRepository,
            RoomRealtimePublisher realtimePublisher) {
        this.roomRepository = roomRepository;
        this.deviceRepository = deviceRepository;
        this.sessionTokenRepository = sessionTokenRepository;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public CreateRoomResponse createRoom(CreateRoomRequest request) {
        Instant now = Instant.now();
        int ttlMinutes = request.ttlMinutes() == null
                ? 60 : request.ttlMinutes();

        String pin = String.format("%06d", secureRandom.nextInt(1_000_000));
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);

        String salt = Base64.getEncoder().encodeToString(saltBytes);
        String pinHash = hashPin(pin, saltBytes);

        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setCode(generateRoomCode());
        room.setRoomName(request.roomName());
        room.setPinHash(pinHash);
        room.setPinSalt(salt);
        room.setAllowFileSharing(
                request.allowFileSharing() == null
                        || request.allowFileSharing());
        room.setMaxMembers(
                request.maxMembers() == null ? 8 : request.maxMembers());
        room.setStatus(Room.RoomStatus.ACTIVE);
        room.setCreatedAt(now);
        room.setLastActiveAt(now);
        room.setExpiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES));
        room.setQuickJoin(Boolean.TRUE.equals(request.quickJoin()));
        room.setHostDeviceId(null);

        byte[] recoveryBytes = new byte[32];
        secureRandom.nextBytes(recoveryBytes);
        String recoveryCredential = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(recoveryBytes);
        room.setHostRecoveryHash(sha256(recoveryCredential));
        room.setHostRecoveryPinHash(
                hostRecoveryPinEncoder.encode(request.hostRecoveryPin()));

        // Save the room first; host_device_id is temporarily null.
        roomRepository.saveAndFlush(room);

        Device device = new Device();
        device.setId(UUID.randomUUID());
        device.setRoomId(room.getId());
        device.setName(request.deviceName());
        device.setPlatform(request.platform());
        device.setRole(Device.DeviceRole.HOST);
        device.setStatus(Device.DeviceStatus.ACTIVE);
        device.setJoinedAt(now);
        device.setLastSeenAt(now);
        device.setPublicKeyJwk(request.publicKey());

        deviceRepository.saveAndFlush(device);

        // Link the newly created host device back to the room.
        room.setHostDeviceId(device.getId());
        roomRepository.save(room);

        // Generate a 256-bit opaque token; persist only its SHA-256 hash.
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);

        SessionToken sessionToken = new SessionToken();
        sessionToken.setId(UUID.randomUUID());
        sessionToken.setDeviceId(device.getId());
        sessionToken.setTokenHash(sha256(rawToken));
        sessionToken.setCreatedAt(now);
        sessionToken.setExpiresAt(room.getExpiresAt());
        sessionToken.setRevoked(false);

        sessionTokenRepository.save(sessionToken);

        return new CreateRoomResponse(
                room.getId(),
                room.getCode(),
                pin,
                room.getExpiresAt(),
                device.getId(),
                rawToken,
                "HOST",
                recoveryCredential);
    }

    @Transactional
    public JoinRoomResponse joinRoom(JoinRoomRequest request) {
    Instant now = Instant.now();

    String code = request.code().trim().toUpperCase(Locale.ROOT);
    String deviceName = request.deviceName().trim();
    String platform = request.platform().trim();

    Room room = roomRepository.findByCode(code)
            .orElseThrow(() ->
                    new IllegalArgumentException("Invalid room code or PIN."));

    // Reject rooms that are not active or have expired.
    if (room.getStatus() != Room.RoomStatus.ACTIVE
            || !room.getExpiresAt().isAfter(now)) {
        if (!room.getExpiresAt().isAfter(now)
                || room.getStatus() == Room.RoomStatus.EXPIRED) {
            throw new RoomExpiredException(room.getExpiresAt());
        }
        throw new RoomClosedException();
    }

    // Verify the supplied PIN against the stored PBKDF2 hash.
    byte[] saltBytes = Base64.getDecoder()
            .decode(room.getPinSalt());

    String suppliedPinHash = hashPin(request.pin(), saltBytes);

    boolean pinMatches = MessageDigest.isEqual(
            Base64.getDecoder().decode(room.getPinHash()),
            Base64.getDecoder().decode(suppliedPinHash));

    if (!pinMatches) {
        throw new IllegalArgumentException("Invalid room code or PIN.");
    }

    // Check room capacity.
    List<Device> devices = deviceRepository.findByRoomId(room.getId());

    Instant activeAfter = now.minusSeconds(MEMBER_IDLE_SECONDS);
    long activeMembers = devices.stream()
            .filter(device ->
                    device.getStatus() == Device.DeviceStatus.ACTIVE
                            && device.getLastSeenAt().isAfter(activeAfter))
            .count();

    if (activeMembers >= room.getMaxMembers()) {
        throw new IllegalArgumentException("This room is full.");
    }

    // Create the guest device.
    Device guest = new Device();
    guest.setId(UUID.randomUUID());
    guest.setRoomId(room.getId());
    guest.setName(deviceName);
    guest.setPlatform(platform);
    guest.setRole(Device.DeviceRole.GUEST);
    guest.setStatus(Device.DeviceStatus.ACTIVE);
    guest.setJoinedAt(now);
    guest.setLastSeenAt(now);
    guest.setPublicKeyJwk(null);

    deviceRepository.save(guest);
    realtimePublisher.refreshMemberCount(room.getId());

    // Generate a session token; only its hash is persisted.
    byte[] tokenBytes = new byte[32];
    secureRandom.nextBytes(tokenBytes);

    String rawToken = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(tokenBytes);

    SessionToken sessionToken = new SessionToken();
    sessionToken.setId(UUID.randomUUID());
    sessionToken.setDeviceId(guest.getId());
    sessionToken.setTokenHash(sha256(rawToken));
    sessionToken.setCreatedAt(now);
    sessionToken.setExpiresAt(room.getExpiresAt());
    sessionToken.setRevoked(false);

    sessionTokenRepository.save(sessionToken);

    room.setLastActiveAt(now);
    roomRepository.save(room);

    return new JoinRoomResponse(
            room.getId(),
            room.getCode(),
            guest.getId(),
            rawToken,
            "GUEST",
            room.getExpiresAt());
}

    @Transactional(readOnly = true)
    public RoomDetailsResponse getRoomDetails(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        ensureRoomActive(room);

        Instant activeAfter = Instant.now()
                .minusSeconds(MEMBER_IDLE_SECONDS);
        long memberCount = deviceRepository.findByRoomId(roomId).stream()
                .filter(device ->
                        device.getStatus() == Device.DeviceStatus.ACTIVE
                                && device.getLastSeenAt().isAfter(activeAfter))
                .count();

        return new RoomDetailsResponse(
                room.getId(),
                room.getCode(),
                room.getRoomName(),
                room.isAllowFileSharing(),
                room.getMaxMembers(),
                room.getStatus(),
                room.getCreatedAt(),
                room.getExpiresAt(),
                memberCount);
    }

    @Transactional
    public JoinRoomResponse recoverHost(RecoverHostRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        Room room = roomRepository.findByCode(code)
                .orElseThrow(InvalidHostRecoveryException::new);
        ensureRoomActive(room);

        String suppliedHash = sha256(request.recoveryCredential());
        if (room.getHostRecoveryHash() == null
                || !MessageDigest.isEqual(
                        room.getHostRecoveryHash()
                                .getBytes(StandardCharsets.UTF_8),
                        suppliedHash.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidHostRecoveryException();
        }

        Instant now = Instant.now();
        Device host = findHostDevice(room);
        host.setStatus(Device.DeviceStatus.ACTIVE);
        host.setLastSeenAt(now);
        deviceRepository.save(host);
        realtimePublisher.refreshMemberCount(room.getId());

        String rawToken = createSessionToken(host, now, room.getExpiresAt());
        room.setLastActiveAt(now);
        roomRepository.save(room);
        return new JoinRoomResponse(
                room.getId(),
                room.getCode(),
                host.getId(),
                rawToken,
                "HOST",
                room.getExpiresAt());
    }

    @Transactional(noRollbackFor = {
            InvalidHostRecoveryException.class,
            HostRecoveryLockedException.class
    })
    public JoinRoomResponse recoverHostWithPin(RecoverHostPinRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        Room room = roomRepository.findByCodeForUpdate(code)
                .orElseThrow(InvalidHostRecoveryException::new);
        ensureRoomActive(room);
        Instant now = Instant.now();
        if (room.getHostRecoveryPinLockedUntil() != null
                && room.getHostRecoveryPinLockedUntil().isAfter(now)) {
            throw new HostRecoveryLockedException();
        }

        boolean roomPinMatches = matchesRoomPin(room, request.roomPin());
        boolean hostPinMatches = room.getHostRecoveryPinHash() != null
                && hostRecoveryPinEncoder.matches(
                        request.hostRecoveryPin(),
                        room.getHostRecoveryPinHash());
        if (!roomPinMatches || !hostPinMatches) {
            int attempts = room.getHostRecoveryPinFailedAttempts() + 1;
            room.setHostRecoveryPinFailedAttempts(attempts);
            if (attempts >= MAX_HOST_PIN_ATTEMPTS) {
                room.setHostRecoveryPinLockedUntil(
                        now.plus(HOST_PIN_LOCK_MINUTES, ChronoUnit.MINUTES));
            }
            roomRepository.save(room);
            if (attempts >= MAX_HOST_PIN_ATTEMPTS) {
                throw new HostRecoveryLockedException();
            }
            throw new InvalidHostRecoveryException();
        }

        room.setHostRecoveryPinFailedAttempts(0);
        room.setHostRecoveryPinLockedUntil(null);
        Device host = findHostDevice(room);
        host.setStatus(Device.DeviceStatus.ACTIVE);
        host.setLastSeenAt(now);
        deviceRepository.save(host);
        realtimePublisher.refreshMemberCount(room.getId());
        String rawToken = createSessionToken(host, now, room.getExpiresAt());
        room.setLastActiveAt(now);
        roomRepository.save(room);
        return new JoinRoomResponse(
                room.getId(),
                room.getCode(),
                host.getId(),
                rawToken,
                "HOST",
                room.getExpiresAt());
    }

    @Transactional
    public void closeRoom(UUID roomId, Device requester) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        ensureRoomActive(room);
        if (requester.getRole() != Device.DeviceRole.HOST
                || !requester.getId().equals(room.getHostDeviceId())
                || !requester.getRoomId().equals(roomId)) {
            throw new UnauthorizedRoomAccessException();
        }
        room.setStatus(Room.RoomStatus.CLOSED);
        Instant now = Instant.now();
        for (Device device : deviceRepository.findByRoomId(roomId)) {
            if (device.getStatus() != Device.DeviceStatus.REMOVED) {
                device.setStatus(Device.DeviceStatus.DISCONNECTED);
                device.setLastSeenAt(now);
                deviceRepository.save(device);
            }
        }
        roomRepository.save(room);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        realtimePublisher.publishRoomClosed(roomId);
                    }
                });
    }

    private boolean matchesRoomPin(Room room, String suppliedPin) {
        byte[] saltBytes = Base64.getDecoder().decode(room.getPinSalt());
        String suppliedPinHash = hashPin(suppliedPin, saltBytes);
        return MessageDigest.isEqual(
                Base64.getDecoder().decode(room.getPinHash()),
                Base64.getDecoder().decode(suppliedPinHash));
    }

    private Device findHostDevice(Room room) {
        if (room.getHostDeviceId() == null) {
            throw new InvalidHostRecoveryException();
        }
        return deviceRepository.findById(room.getHostDeviceId())
                .filter(device -> device.getRoomId().equals(room.getId())
                        && device.getRole() == Device.DeviceRole.HOST
                        && device.getStatus() != Device.DeviceStatus.REMOVED)
                .orElseThrow(InvalidHostRecoveryException::new);
    }

    @Transactional
    public void updatePresence(UUID roomId, UUID deviceId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        ensureRoomActive(room);
        Device device = deviceRepository.findById(deviceId)
                .filter(candidate -> candidate.getRoomId().equals(roomId)
                        && candidate.getStatus() != Device.DeviceStatus.REMOVED)
                .orElseThrow(UnauthorizedRoomAccessException::new);
        device.setStatus(Device.DeviceStatus.ACTIVE);
        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);
        realtimePublisher.refreshMemberCount(roomId);
    }

    @Transactional
    public void leaveRoom(UUID roomId, UUID deviceId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        ensureRoomActive(room);
        Device device = deviceRepository.findById(deviceId)
                .filter(candidate -> candidate.getRoomId().equals(roomId))
                .orElseThrow(UnauthorizedRoomAccessException::new);
        if (device.getStatus() != Device.DeviceStatus.REMOVED) {
            device.setStatus(Device.DeviceStatus.DISCONNECTED);
            device.setLastSeenAt(Instant.now());
            deviceRepository.save(device);
            sessionTokenRepository.findByDeviceId(deviceId).forEach(token -> {
                token.setRevoked(true);
                sessionTokenRepository.save(token);
            });
            realtimePublisher.refreshMemberCount(roomId);
        }
    }

    private void ensureRoomActive(Room room) {
        if (!room.getExpiresAt().isAfter(Instant.now())
                || room.getStatus() == Room.RoomStatus.EXPIRED) {
            throw new RoomExpiredException(room.getExpiresAt());
        }
        if (room.getStatus() != Room.RoomStatus.ACTIVE) {
            throw new RoomClosedException();
        }
    }

    private String createSessionToken(
            Device device, Instant now, Instant expiresAt) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);

        SessionToken sessionToken = new SessionToken();
        sessionToken.setId(UUID.randomUUID());
        sessionToken.setDeviceId(device.getId());
        sessionToken.setTokenHash(sha256(rawToken));
        sessionToken.setCreatedAt(now);
        sessionToken.setExpiresAt(expiresAt);
        sessionToken.setRevoked(false);
        sessionTokenRepository.save(sessionToken);
        return rawToken;
    }

    public static class RoomExpiredException extends RuntimeException {
        private final Instant expiresAt;
        public RoomExpiredException(Instant expiresAt) {
            super("ROOM_EXPIRED");
            this.expiresAt = expiresAt;
        }
        public Instant getExpiresAt() { return expiresAt; }
    }

    public static class RoomClosedException extends RuntimeException {
        public RoomClosedException() { super("ROOM_CLOSED"); }
    }

    public static class RoomNotFoundException extends RuntimeException {
        public RoomNotFoundException() { super("ROOM_NOT_FOUND"); }
    }

    public static class InvalidHostRecoveryException extends RuntimeException {
        public InvalidHostRecoveryException() {
            super("HOST_RECOVERY_INVALID");
        }
    }

    public static class HostRecoveryLockedException extends RuntimeException {
        public HostRecoveryLockedException() {
            super("HOST_RECOVERY_LOCKED");
        }
    }

    public static class UnauthorizedRoomAccessException
            extends RuntimeException {
        public UnauthorizedRoomAccessException() {
            super("ROOM_ACCESS_DENIED");
        }
    }

    private String generateRoomCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder();

            for (int i = 0; i < 8; i++) {
                code.append(alphabet.charAt(
                        secureRandom.nextInt(alphabet.length())));
            }

            if (!roomRepository.existsByCode(code.toString())) {
                return code.toString();
            }
        }

        throw new IllegalStateException(
                "Could not generate a unique room code. Try again.");
    }

    private String hashPin(String pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(
                pin.toCharArray(), salt, 210_000, 256);

        try {
            byte[] hash = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();

            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash room PIN.", ex);
        } finally {
            spec.clearPassword();
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not hash session token.", ex);
        }
    }
}
