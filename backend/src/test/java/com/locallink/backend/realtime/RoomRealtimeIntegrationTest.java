package com.locallink.backend.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.locallink.backend.chat.ChatMessageRequest;
import com.locallink.backend.chat.ChatMessageService;
import com.locallink.backend.device.Device;
import com.locallink.backend.device.DeviceRepository;
import com.locallink.backend.room.CreateRoomRequest;
import com.locallink.backend.room.CreateRoomResponse;
import com.locallink.backend.room.JoinRoomRequest;
import com.locallink.backend.room.JoinRoomResponse;
import com.locallink.backend.room.RoomService;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomRealtimeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RoomService roomService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ChatMessageService chatMessageService;

    @Test
    void authenticatedMembersReceivePersistedMessagesWithoutPolling()
            throws Exception {
        CreateRoomResponse room = roomService.createRoom(new CreateRoomRequest(
                "Realtime test", "Host", "WEB", true, 8, 60, false,
                "1234", null));
        JoinRoomResponse guest = roomService.joinRoom(new JoinRoomRequest(
                room.code(), room.pin(), "Guest", "WEB"));

        WebSocketStompClient client =
                new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        client.setTaskScheduler(new ConcurrentTaskScheduler());
        AtomicReference<StompSession> hostSession = new AtomicReference<>();
        AtomicReference<StompSession> guestSession = new AtomicReference<>();
        AtomicReference<String> websocketError = new AtomicReference<>();
        try {
            StompSessionHandlerAdapter handler =
                    new StompSessionHandlerAdapter() {
                        @Override
                        public void handleTransportError(
                                StompSession session, Throwable exception) {
                            websocketError.set(exception.getMessage());
                        }
                    };
            hostSession.set(connect(client, room.sessionToken(), handler));
            guestSession.set(connect(client, guest.sessionToken(), handler));

            CountDownLatch delivered = new CountDownLatch(2);
            ConcurrentLinkedQueue<java.util.Map<?, ?>> received =
                    new ConcurrentLinkedQueue<>();
            StompHeaders subscription = new StompHeaders();
            subscription.setDestination(
                    "/topic/rooms/" + room.roomId() + "/messages");
            subscription.setId("message-subscription");
            hostSession.get().subscribe(subscription,
                    new StompFrameHandler() {
                        @Override
                        public java.lang.reflect.Type getPayloadType(
                                StompHeaders headers) {
                            return java.util.Map.class;
                        }

                        @Override
                        public void handleFrame(
                                StompHeaders headers, Object payload) {
                            received.add((java.util.Map<?, ?>) payload);
                            delivered.countDown();
                        }
                    });
            Thread.sleep(300);
            Device authenticatedGuest = deviceRepository.findById(
                    guest.deviceId()).orElseThrow();
            chatMessageService.send(
                    room.roomId(),
                    authenticatedGuest,
                    new ChatMessageRequest(UUID.randomUUID(), "Hello live"));
            guestSession.get().send(
                    "/app/rooms/" + room.roomId() + "/messages",
                    new ChatMessageRequest(
                            UUID.randomUUID(), "Sent over STOMP"));

            assertTrue(delivered.await(5, TimeUnit.SECONDS),
                    () -> "No live message received: " + websocketError.get());
            assertEquals(2, received.size());
            assertTrue(received.stream().anyMatch(message ->
                    "Hello live".equals(message.get("content"))));
            assertTrue(received.stream().anyMatch(message ->
                    "Sent over STOMP".equals(message.get("content"))));
            for (java.util.Map<?, ?> message : received) {
                assertEquals(guest.deviceId().toString(),
                        message.get("senderDeviceId"));
                assertEquals("Guest", message.get("senderName"));
            }
        } finally {
            if (hostSession.get() != null) {
                hostSession.get().disconnect();
            }
            if (guestSession.get() != null) {
                guestSession.get().disconnect();
            }
            client.stop();
        }
    }

    private StompSession connect(
            WebSocketStompClient client,
            String sessionToken,
            StompSessionHandlerAdapter handler) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + sessionToken);
        CompletableFuture<StompSession> connection = client.connectAsync(
                URI.create("ws://localhost:" + port + "/ws"),
                new WebSocketHttpHeaders(),
                connectHeaders,
                handler);
        return connection.get(8, TimeUnit.SECONDS);
    }
}
