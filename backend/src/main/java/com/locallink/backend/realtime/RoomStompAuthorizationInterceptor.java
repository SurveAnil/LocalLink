package com.locallink.backend.realtime;

import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import com.locallink.backend.device.Device;
import com.locallink.backend.session.SessionTokenService;

public class RoomStompAuthorizationInterceptor implements ChannelInterceptor {

    private static final String DEVICE_ID = "authenticatedDeviceId";
    private static final String ROOM_ID = "authenticatedRoomId";
    private static final String TOKEN_FINGERPRINT = "sessionTokenFingerprint";

    private final SessionTokenService sessionTokenService;

    public RoomStompAuthorizationInterceptor(
            SessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        MessageHeaderAccessor mutableAccessor =
                MessageHeaderAccessor.getMutableAccessor(message);
        if (!(mutableAccessor instanceof StompHeaderAccessor accessor)) {
            return message;
        }
        if (accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticate(accessor);
            return mutableMessage(message, accessor);
        }

        if (accessor.getMessageType() == SimpMessageType.DISCONNECT
                || accessor.getCommand() == StompCommand.DISCONNECT) {
            return mutableMessage(message, accessor);
        }

        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null || !attributes.containsKey(DEVICE_ID)) {
            throw new MessageDeliveryException(
                    message, "An authenticated room session is required.");
        }

        Device device;
        try {
            device = sessionTokenService.validateTokenFingerprint(
                    (String) attributes.get(TOKEN_FINGERPRINT));
        } catch (RuntimeException exception) {
            throw new MessageDeliveryException(
                    message, "The room session is no longer valid.", exception);
        }
        String roomId = (String) attributes.get(ROOM_ID);
        if (!device.getRoomId().toString().equals(roomId)
                || !device.getId().toString()
                        .equals(attributes.get(DEVICE_ID))) {
            throw new MessageDeliveryException(
                    message, "The room session is no longer authorized.");
        }
        String destination = accessor.getDestination();
        if (destination == null || !isAllowedDestination(destination, roomId,
                accessor.getCommand())) {
            throw new MessageDeliveryException(
                    message, "This room destination is not authorized.");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessageDeliveryException(
                    "A valid room session token is required.");
        }

        Device device;
        try {
            device = sessionTokenService.validateToken(
                    authorization.substring("Bearer ".length()).trim());
        } catch (RuntimeException exception) {
            throw new MessageDeliveryException(
                    "The room session token is invalid or expired.");
        }

        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            throw new MessageDeliveryException(
                    "The WebSocket session could not be authenticated.");
        }
        attributes.put(DEVICE_ID, device.getId().toString());
        attributes.put(ROOM_ID, device.getRoomId().toString());
        attributes.put(TOKEN_FINGERPRINT, fingerprint(authorization.substring(
                "Bearer ".length()).trim()));
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                device.getId().toString(),
                null,
                AuthorityUtils.createAuthorityList(
                        "ROLE_" + device.getRole().name())));
    }

    private boolean isAllowedDestination(
            String destination, String roomId, StompCommand command) {
        String roomPrefix = "/topic/rooms/" + roomId + "/";
        if (command == StompCommand.SUBSCRIBE) {
            return destination.equals(roomPrefix + "messages")
                    || destination.equals(roomPrefix + "members")
                    || destination.equals(roomPrefix + "files");
        }
        if (command == StompCommand.SEND) {
            return destination.equals("/app/rooms/" + roomId + "/messages");
        }
        return false;
    }

    static String deviceIdAttribute() {
        return DEVICE_ID;
    }

    static String roomIdAttribute() {
        return ROOM_ID;
    }

    private String fingerprint(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Could not validate the WebSocket session.", exception);
        }
    }

    private Message<?> mutableMessage(
            Message<?> message, StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(
                message.getPayload(), accessor.getMessageHeaders());
    }
}
