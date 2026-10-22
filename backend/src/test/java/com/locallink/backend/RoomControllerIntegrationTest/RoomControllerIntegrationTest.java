package com.locallink.backend.RoomControllerIntegrationTest;

import java.time.Instant;
import java.util.UUID;

import com.locallink.backend.device.Device;
import com.locallink.backend.device.DeviceRepository;
import com.locallink.backend.files.RoomFileRepository;
import com.locallink.backend.files.RoomFileService;
import com.locallink.backend.room.Room;
import com.locallink.backend.room.RoomRepository;
import com.locallink.backend.session.SessionToken;
import com.locallink.backend.session.SessionTokenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "locallink.files.max-size-bytes=3",
        "locallink.files.storage-directory=target/test-room-files",
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=2KB"
})
@AutoConfigureMockMvc
class RoomControllerIntegrationTest {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SessionTokenRepository sessionTokenRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private RoomFileRepository roomFileRepository;

    @Autowired
    private RoomFileService roomFileService;

    @Test
    void createRoomWithValidRequestReturnsCreatedRoom() throws Exception {
        String requestJson = """
                {
                    "roomName": "Integration Test Room",
                    "deviceName": "Test Host",
                    "platform": "Windows",
                    "allowFileSharing": true,
                    "maxMembers": 8,
                    "ttlMinutes": 60,
                    "quickJoin": false,
                    "hostRecoveryPin": "1234"
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").isNotEmpty())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.code").value(
                        org.hamcrest.Matchers.matchesRegex(
                                "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}")))
                .andExpect(jsonPath("$.pin").value(
                        org.hamcrest.Matchers.matchesRegex("\\d{6}")))
                .andExpect(jsonPath("$.deviceId").isNotEmpty())
                .andExpect(jsonPath("$.sessionToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("HOST"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.hostRecoveryPin").doesNotExist());
    }

    @Test
    void createRoomWithMissingRequiredFieldsReturnsBadRequest()
            throws Exception {

        String invalidRequestJson = """
                {
                    "roomName": "",
                    "deviceName": "",
                    "platform": "",
                    "allowFileSharing": null,
                    "maxMembers": 8,
                    "ttlMinutes": 60,
                    "quickJoin": null
                }
                """;

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoomRejectsMalformedHostRecoveryPin() throws Exception {
        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName":"PIN validation",
                                  "deviceName":"Host",
                                  "platform":"WEB",
                                  "allowFileSharing":true,
                                  "maxMembers":8,
                                  "ttlMinutes":60,
                                  "quickJoin":false,
                                  "hostRecoveryPin":"12a4"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }


    private TestRoom roomA;
    private TestRoom roomB;

    @BeforeEach
    void createRoomsForAuthorizationTests() throws Exception {
        roomA = createTestRoom("Authorization Room A");
        roomB = createTestRoom("Authorization Room B");
    }

    @Test
    void hostCanAccessOwnRoom() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk());
    }

    @Test
    void secondHostCanAccessOwnRoom() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomB.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomB.token()))
                .andExpect(status().isOk());
    }

    @Test
    void hostCannotAccessAnotherRoom() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomB.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-test-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeRoomReturnsExpiryAndActualMemberCount() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.pinHash").doesNotExist());
    }

    @Test
    void expiredRoomReturnsGoneInsteadOfSessionError() throws Exception {
        Room room = roomRepository.findById(roomA.roomId()).orElseThrow();
        room.setExpiresAt(Instant.now().minusSeconds(1));
        roomRepository.save(room);

        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("ROOM_EXPIRED"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void expiredSessionTokenHasDistinctError() throws Exception {
        SessionToken token = sessionTokenRepository.findAll().stream()
                .filter(candidate ->
                        candidate.getDeviceId().equals(roomA.deviceId()))
                .findFirst()
                .orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(1));
        sessionTokenRepository.save(token);

        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("SESSION_TOKEN_EXPIRED"));
    }

    @Test
    void hostCanRecoverWithRecoveryCredential() throws Exception {
        String response = mockMvc.perform(post("/api/v1/rooms/recover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","recoveryCredential":"%s"}
                                """.formatted(
                                roomA.code(), roomA.recoveryCredential())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HOST"))
                .andExpect(jsonPath("$.sessionToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String recoveredToken = com.jayway.jsonpath.JsonPath
                .read(response, "$.sessionToken").toString();

        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + recoveredToken))
                .andExpect(status().isOk());
    }

    @Test
    void hostCanRecoverOnAnotherDeviceWithRoomAndRecoveryPins()
            throws Exception {
        String response = mockMvc.perform(post("/api/v1/rooms/recover-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","roomPin":"%s","hostRecoveryPin":"1234"}
                                """.formatted(roomA.code(), roomA.pin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HOST"))
                .andExpect(jsonPath("$.sessionToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String recoveredToken = com.jayway.jsonpath.JsonPath
                .read(response, "$.sessionToken").toString();
        mockMvc.perform(get("/api/v1/rooms/{roomId}/me", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + recoveredToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Automated Test Host"))
                .andExpect(jsonPath("$.role").value("HOST"));
    }

    @Test
    void incorrectHostRecoveryPinIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/recover-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","roomPin":"%s","hostRecoveryPin":"9999"}
                                """.formatted(roomA.code(), roomA.pin())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("HOST_RECOVERY_INVALID"));
    }

    @Test
    void repeatedIncorrectHostRecoveryPinsTriggerTemporaryLockout()
            throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/api/v1/rooms/recover-pin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"%s","roomPin":"%s","hostRecoveryPin":"9999"}
                                    """.formatted(roomA.code(), roomA.pin())))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/rooms/recover-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","roomPin":"%s","hostRecoveryPin":"9999"}
                                """.formatted(roomA.code(), roomA.pin())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("HOST_RECOVERY_LOCKED"));
    }

    @Test
    void invalidHostRecoveryCredentialIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/recover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","recoveryCredential":"incorrect"}
                                """.formatted(roomA.code())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("HOST_RECOVERY_INVALID"));
    }

    @Test
    void createRoomStoresOnlyHashedHostRecoveryPin() throws Exception {
        Room room = roomRepository.findByCode(roomA.code()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(
                room.getHostRecoveryPinHash());
        org.junit.jupiter.api.Assertions.assertNotEquals(
                "1234", room.getHostRecoveryPinHash());
        org.junit.jupiter.api.Assertions.assertTrue(
                room.getHostRecoveryPinHash().startsWith("$2"));
    }

    @Test
    void roomCodeAndPinAloneCannotRecoverHostAccess() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/recover-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","roomPin":"%s"}
                                """.formatted(roomA.code(), roomA.pin())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void onlyHostCanCloseRoomAndClosureInvalidatesRoomAccess()
            throws Exception {
        String joinResponse = mockMvc.perform(post("/api/v1/rooms/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","pin":"%s","deviceName":"Guest","platform":"WEB"}
                                """.formatted(roomA.code(), roomA.pin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String guestToken = com.jayway.jsonpath.JsonPath
                .read(joinResponse, "$.sessionToken").toString();
        mockMvc.perform(post("/api/v1/rooms/{roomId}/close", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + guestToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/close", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + guestToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("ROOM_CLOSED"));
    }

    @Test
    void memberCountChangesWhenGuestJoinsAndLeaves() throws Exception {
        String joinResponse = mockMvc.perform(post("/api/v1/rooms/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "pin":"%s",
                                  "deviceName":"Guest",
                                  "platform":"WEB"
                                }
                                """.formatted(roomA.code(), roomA.pin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String guestToken = com.jayway.jsonpath.JsonPath
                .read(joinResponse, "$.sessionToken").toString();
        String guestDeviceId = com.jayway.jsonpath.JsonPath
                .read(joinResponse, "$.deviceId").toString();

        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2));

        mockMvc.perform(post("/api/v1/rooms/{roomId}/leave", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + guestToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(1));

        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/messages", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + guestToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("SESSION_TOKEN_INVALID"));

        org.junit.jupiter.api.Assertions.assertEquals(
                com.locallink.backend.device.Device.DeviceStatus.DISCONNECTED,
                deviceRepository.findById(UUID.fromString(guestDeviceId))
                        .orElseThrow().getStatus());
    }

    @Test
    void memberCountExcludesIdleDevices() throws Exception {
        Device host = deviceRepository.findById(roomA.deviceId())
                        .orElseThrow();
        host.setLastSeenAt(Instant.now().minusSeconds(121));
        deviceRepository.save(host);

        mockMvc.perform(get("/api/v1/rooms/{roomId}", roomA.roomId())
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + roomA.token()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.memberCount").value(0));
    }

    @Test
    void membersCanSendAndRetrievePersistedRoomMessages() throws Exception {
        String response = mockMvc.perform(post(
                        "/api/v1/rooms/{roomId}/messages", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientMessageId":"%s",
                                  "content":"Hello from host"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderDeviceId")
                        .value(roomA.deviceId().toString()))
                .andExpect(jsonPath("$.senderName")
                        .value("Automated Test Host"))
                .andExpect(jsonPath("$.content").value("Hello from host"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String messageId = com.jayway.jsonpath.JsonPath
                .read(response, "$.id").toString();

        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/messages", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(messageId))
                .andExpect(jsonPath("$[0].content").value("Hello from host"));
    }

    @Test
    void messageRetriesWithSameClientIdDoNotCreateDuplicates() throws Exception {
        String clientMessageId = UUID.randomUUID().toString();
        String payload = """
                {"clientMessageId":"%s","content":"Retry-safe message"}
                """.formatted(clientMessageId);
        String firstResponse = mockMvc.perform(post(
                        "/api/v1/rooms/{roomId}/messages", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstId = com.jayway.jsonpath.JsonPath
                .read(firstResponse, "$.id").toString();

        mockMvc.perform(post(
                        "/api/v1/rooms/{roomId}/messages", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId));

        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/messages", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void messagesRequireValidSessionAndCannotCrossRooms() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/messages", roomA.roomId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(
                        "/api/v1/rooms/{roomId}/messages", roomB.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientMessageId":"%s","content":"forbidden"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ROOM_ACCESS_DENIED"));
    }

    @Test
    void expiredRoomRejectsMessageRequests() throws Exception {
        Room room = roomRepository.findById(roomA.roomId()).orElseThrow();
        room.setExpiresAt(Instant.now().minusSeconds(1));
        roomRepository.save(room);

        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/messages", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("ROOM_EXPIRED"));
    }

    @Test
    void fileUploadDownloadAndListingWorkForRoomMembers() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "..\\private.txt", "text/plain",
                "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .multipart("/api/v1/rooms/{roomId}/files",
                                        roomA.roomId())
                                .file(upload)
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename")
                        .value("private.txt"))
                .andExpect(jsonPath("$.uploaderDeviceId")
                        .value(roomA.deviceId().toString()))
                .andExpect(jsonPath("$.sizeBytes").value(3))
                .andReturn().getResponse().getContentAsString();
        String fileId = com.jayway.jsonpath.JsonPath
                .read(response, "$.id").toString();

        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/files", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(fileId));

        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/files/{fileId}/content",
                        roomA.roomId(), fileId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.content().bytes(
                                "abc".getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void uploadRetryWithSameIdReturnsTheExistingFile() throws Exception {
        UUID uploadId = UUID.randomUUID();
        MockMultipartFile firstUpload = new MockMultipartFile(
                "file", "retry.txt", "text/plain", new byte[] { 1 });
        String firstResponse = mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .multipart("/api/v1/rooms/{roomId}/files",
                                        roomA.roomId())
                                .file(firstUpload)
                                .header("X-Upload-Id", uploadId)
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = com.jayway.jsonpath.JsonPath
                .read(firstResponse, "$.id").toString();

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .multipart("/api/v1/rooms/{roomId}/files",
                                        roomA.roomId())
                                .file(new MockMultipartFile(
                                        "file", "retry.txt", "text/plain",
                                        new byte[] { 1 }))
                                .header("X-Upload-Id", uploadId)
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId));
        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .multipart("/api/v1/rooms/{roomId}/files",
                                        roomA.roomId())
                                .file(new MockMultipartFile(
                                        "file", "different.txt", "text/plain",
                                        new byte[] { 1 }))
                                .header("X-Upload-Id", uploadId)
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + roomA.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("UPLOAD_ID_CONFLICT"));
        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/files", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void fileListingCanPageThroughAllRoomFiles() throws Exception {
        uploadOneByteFile(roomA);
        uploadOneByteFile(roomA);
        uploadOneByteFile(roomA);
        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/files", roomA.roomId())
                        .queryParam("page", "0")
                        .queryParam("size", "2")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/files", roomA.roomId())
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void fileEndpointsRejectInvalidCredentialsAndCrossRoomAccess()
            throws Exception {
        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/files", roomA.roomId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        MockMultipartFile upload = new MockMultipartFile(
                "file", "room-a.txt", "text/plain", new byte[] { 1 });
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .multipart("/api/v1/rooms/{roomId}/files",
                                        roomA.roomId())
                                .file(upload)
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = com.jayway.jsonpath.JsonPath
                .read(response, "$.id").toString();

        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/files/{fileId}/content",
                        roomB.roomId(), fileId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomB.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ROOM_FILE_NOT_FOUND"));

        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/files", roomB.roomId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isForbidden());
    }

    @SuppressWarnings("deprecation")
@Test
    void uploadRejectsFilesAboveConfiguredLimit() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "too-large.txt", "text/plain",
                new byte[] { 1, 2, 3, 4 });

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .multipart("/api/v1/rooms/{roomId}/files",
                                        roomA.roomId())
                                .file(upload)
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + roomA.token()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"));
    }

    @Test
    void expiredRoomRejectsFileUploadAndDownload() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "file.txt", "text/plain", new byte[] { 1 });
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .multipart("/api/v1/rooms/{roomId}/files",
                                        roomA.roomId())
                                .file(upload)
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + roomA.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = com.jayway.jsonpath.JsonPath
                .read(response, "$.id").toString();
        Room room = roomRepository.findById(roomA.roomId()).orElseThrow();
        room.setExpiresAt(Instant.now().minusSeconds(1));
        roomRepository.save(room);

        mockMvc.perform(get(
                        "/api/v1/rooms/{roomId}/files/{fileId}/content",
                        roomA.roomId(), fileId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + roomA.token()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("ROOM_EXPIRED"));
    }

    @Test
    void expiredFileCleanupKeepsActiveRoomFilesAndRemovesExpiredFiles()
            throws Exception {
        UUID activeFileId = uploadOneByteFile(roomA);
        UUID expiredFileId = uploadOneByteFile(roomB);
        Room expiredRoom = roomRepository.findById(roomB.roomId())
                .orElseThrow();
        expiredRoom.setExpiresAt(Instant.now().minusSeconds(1));
        roomRepository.save(expiredRoom);

        roomFileService.cleanupExpiredRoomFiles();

        org.junit.jupiter.api.Assertions.assertTrue(
                roomFileRepository.existsById(activeFileId));
        org.junit.jupiter.api.Assertions.assertFalse(
                roomFileRepository.existsById(expiredFileId));
    }

    private UUID uploadOneByteFile(TestRoom room) throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "cleanup.txt", "text/plain", new byte[] { 1 });
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .multipart("/api/v1/rooms/{roomId}/files",
                                        room.roomId())
                                .file(upload)
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + room.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath
                .read(response, "$.id").toString());
    }

    private TestRoom createTestRoom(String roomName) throws Exception {
        String requestJson = """
            {
                "roomName": "%s",
                "deviceName": "Automated Test Host",
                "platform": "Windows",
                "allowFileSharing": true,
                "maxMembers": 8,
                "ttlMinutes": 60,
                "quickJoin": false,
                "hostRecoveryPin": "1234"
            }
            """.formatted(roomName);

        MvcResult result = mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").isNotEmpty())
                .andExpect(jsonPath("$.sessionToken").isNotEmpty())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        String roomId = com.jayway.jsonpath.JsonPath
                .read(responseBody, "$.roomId").toString();

        String token = com.jayway.jsonpath.JsonPath
                .read(responseBody, "$.sessionToken").toString();

        String code = com.jayway.jsonpath.JsonPath
                .read(responseBody, "$.code").toString();
        String pin = com.jayway.jsonpath.JsonPath
                .read(responseBody, "$.pin").toString();
        String deviceId = com.jayway.jsonpath.JsonPath
                .read(responseBody, "$.deviceId").toString();
        String recoveryCredential = com.jayway.jsonpath.JsonPath
                .read(responseBody, "$.hostRecoveryCredential").toString();

        return new TestRoom(
                UUID.fromString(roomId),
                token,
                code,
                pin,
                UUID.fromString(deviceId),
                recoveryCredential);
    }

    private record TestRoom(
            UUID roomId,
            String token,
            String code,
            String pin,
            UUID deviceId,
            String recoveryCredential) {}

}
