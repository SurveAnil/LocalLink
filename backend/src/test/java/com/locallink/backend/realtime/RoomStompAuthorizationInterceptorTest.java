package com.locallink.backend.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.locallink.backend.device.Device;
import com.locallink.backend.session.SessionTokenService;

class RoomStompAuthorizationInterceptorTest {

    private final SessionTokenService tokenService =
            mock(SessionTokenService.class);
    private final RoomStompAuthorizationInterceptor interceptor =
            new RoomStompAuthorizationInterceptor(tokenService);
    private final UUID roomId = UUID.randomUUID();
    private final UUID otherRoomId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private Device device;
    private Map<String, Object> sessionAttributes;

    @BeforeEach
    void setUp() {
        device = new Device();
        device.setId(deviceId);
        device.setRoomId(roomId);
        device.setRole(Device.DeviceRole.GUEST);
        device.setStatus(Device.DeviceStatus.ACTIVE);
        when(tokenService.validateToken("valid-token")).thenReturn(device);
        when(tokenService.validateTokenFingerprint(anyString()))
                .thenReturn(device);

        StompHeaderAccessor connect =
                StompHeaderAccessor.create(StompCommand.CONNECT);
        sessionAttributes = new HashMap<>();
        connect.setSessionAttributes(sessionAttributes);
        connect.setNativeHeader("Authorization", "Bearer valid-token");
        interceptor.preSend(message(connect), null);
    }

    @Test
    void authenticatesConnectAndAllowsOwnRoomSubscriptions() {
        StompHeaderAccessor subscribe =
                command(StompCommand.SUBSCRIBE,
                        "/topic/rooms/" + roomId + "/messages");

        interceptor.preSend(message(subscribe), null);

        verify(tokenService).validateTokenFingerprint(anyString());
        assertEquals(deviceId.toString(), sessionAttributes.get(
                RoomStompAuthorizationInterceptor.deviceIdAttribute()));
    }

    @Test
    void allowsRoomFileEventSubscriptionOnlyForAuthenticatedRoom() {
        StompHeaderAccessor subscribe = command(
                StompCommand.SUBSCRIBE,
                "/topic/rooms/" + roomId + "/files");
        interceptor.preSend(message(subscribe), null);

        StompHeaderAccessor crossRoomSubscribe = command(
                StompCommand.SUBSCRIBE,
                "/topic/rooms/" + otherRoomId + "/files");
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(message(crossRoomSubscribe), null));
    }

    @Test
    void rejectsSubscriptionToAnotherRoom() {
        StompHeaderAccessor subscribe =
                command(StompCommand.SUBSCRIBE,
                        "/topic/rooms/" + otherRoomId + "/messages");

        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(message(subscribe), null));
    }

    @Test
    void allowsOnlyRoomScopedMessagePublication() {
        StompHeaderAccessor send = command(
                StompCommand.SEND, "/app/rooms/" + roomId + "/messages");

        interceptor.preSend(message(send), null);

        StompHeaderAccessor crossRoomSend = command(
                StompCommand.SEND,
                "/app/rooms/" + otherRoomId + "/messages");
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(message(crossRoomSend), null));
    }

    @Test
    void rejectsPublicationWhenSessionWasRevoked() {
        when(tokenService.validateTokenFingerprint(anyString()))
                .thenThrow(new SessionTokenService.InvalidSessionException());

        StompHeaderAccessor send = command(
                StompCommand.SEND, "/app/rooms/" + roomId + "/messages");
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(message(send), null));
    }

    private StompHeaderAccessor command(
            StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setDestination(destination);
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());
    }
}
