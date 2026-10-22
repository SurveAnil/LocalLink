
package com.locallink.backend.room;

import jakarta.validation.Valid;
import com.locallink.backend.device.Device;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/{roomId}")
    public RoomDetailsResponse getRoomById(
            @PathVariable UUID roomId,
            HttpServletRequest request) {

        Device device = authenticatedDevice(request);
        assertRoomAccess(device, roomId);
        return roomService.getRoomDetails(roomId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRoomResponse createRoom(
            @Valid @RequestBody CreateRoomRequest request) {
        return roomService.createRoom(request);
    }

    @PostMapping("/join")
    public JoinRoomResponse joinRoom(
            @Valid @RequestBody JoinRoomRequest request) {
        return roomService.joinRoom(request);
    }

    @PostMapping("/recover")
    public JoinRoomResponse recoverHost(
            @Valid @RequestBody RecoverHostRequest request) {
        return roomService.recoverHost(request);
    }

    @PostMapping("/recover-pin")
    public JoinRoomResponse recoverHostWithPin(
            @Valid @RequestBody RecoverHostPinRequest request) {
        return roomService.recoverHostWithPin(request);
    }

    @GetMapping("/{roomId}/me")
    public RoomMemberIdentity getCurrentMember(
            @PathVariable UUID roomId,
            HttpServletRequest request) {
        Device device = authenticatedDevice(request);
        assertRoomAccess(device, roomId);
        return new RoomMemberIdentity(
                device.getId(), device.getName(), device.getRole().name());
    }

    @PostMapping("/{roomId}/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePresence(
            @PathVariable UUID roomId,
            HttpServletRequest request) {
        Device device = authenticatedDevice(request);
        assertRoomAccess(device, roomId);
        roomService.updatePresence(roomId, device.getId());
    }

    @PostMapping("/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveRoom(
            @PathVariable UUID roomId,
            HttpServletRequest request) {
        Device device = authenticatedDevice(request);
        assertRoomAccess(device, roomId);
        roomService.leaveRoom(roomId, device.getId());
    }

    @PostMapping("/{roomId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeRoom(
            @PathVariable UUID roomId,
            HttpServletRequest request) {
        roomService.closeRoom(roomId, authenticatedDevice(request));
    }

    private Device authenticatedDevice(HttpServletRequest request) {
        Device device = (Device) request.getAttribute("authenticatedDevice");
        if (device == null) {
            throw new RoomService.UnauthorizedRoomAccessException();
        }
        return device;
    }

    private void assertRoomAccess(Device device, UUID roomId) {
        if (!device.getRoomId().equals(roomId)) {
            throw new RoomService.UnauthorizedRoomAccessException();
        }
    }
}
