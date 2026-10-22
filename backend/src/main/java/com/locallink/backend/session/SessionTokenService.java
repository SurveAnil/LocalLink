
package com.locallink.backend.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.locallink.backend.device.Device;
import com.locallink.backend.device.DeviceRepository;
import com.locallink.backend.room.Room;
import com.locallink.backend.room.RoomRepository;
import com.locallink.backend.room.RoomService;

@Service
public class SessionTokenService {

    private final SessionTokenRepository sessionTokenRepository;
    private final DeviceRepository deviceRepository;
    private final RoomRepository roomRepository;

    public SessionTokenService(
            SessionTokenRepository sessionTokenRepository,
            DeviceRepository deviceRepository,
            RoomRepository roomRepository) {
        this.sessionTokenRepository = sessionTokenRepository;
        this.deviceRepository = deviceRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public Device validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidSessionException();
        }

        SessionToken session = sessionTokenRepository
                .findByTokenHash(sha256(rawToken))
                .orElseThrow(InvalidSessionException::new);
        return validateSession(session);
    }

    @Transactional(readOnly = true)
    public Device validateTokenFingerprint(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new InvalidSessionException();
        }
        SessionToken session = sessionTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(InvalidSessionException::new);
        return validateSession(session);
    }

    private Device validateSession(SessionToken session) {
        Instant now = Instant.now();

        Device device = deviceRepository.findById(session.getDeviceId())
                .orElseThrow(InvalidSessionException::new);

        if (device.getStatus() == Device.DeviceStatus.REMOVED) {
            throw new InvalidSessionException();
        }

        Room room = roomRepository.findById(device.getRoomId())
                .orElseThrow(InvalidSessionException::new);

        if (room.getStatus() != Room.RoomStatus.ACTIVE
                || !room.getExpiresAt().isAfter(now)) {
            if (room.getStatus() == Room.RoomStatus.EXPIRED
                    || !room.getExpiresAt().isAfter(now)) {
                throw new RoomService.RoomExpiredException(room.getExpiresAt());
            }
            throw new RoomService.RoomClosedException();
        }

        if (session.isRevoked()) {
            throw new InvalidSessionException();
        }

        if (!session.getExpiresAt().isAfter(now)) {
            throw new ExpiredSessionException();
        }

        return device;
    }


    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "Could not hash session token.", ex);
        }
    }


    public static class InvalidSessionException
            extends RuntimeException {

        public InvalidSessionException() {
            super("Session is invalid or has expired.");
        }
    }

    public static class ExpiredSessionException extends RuntimeException {
        public ExpiredSessionException() {
            super("SESSION_EXPIRED");
        }
    }
}
