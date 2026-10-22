package com.locallink.backend.files;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.locallink.backend.device.Device;
import com.locallink.backend.room.RoomService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/files")
public class RoomFileController {

    private final RoomFileService fileService;

    public RoomFileController(RoomFileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public List<RoomFileResponse> list(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            HttpServletRequest request) {
        requireRoomMember(roomId, request);
        return fileService.list(roomId, page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RoomFileResponse upload(
            @PathVariable UUID roomId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Upload-Id", required = false)
            UUID clientUploadId,
            HttpServletRequest request) {
        return fileService.upload(
                roomId,
                requireRoomMember(roomId, request),
                file,
                clientUploadId);
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> download(
            @PathVariable UUID roomId,
            @PathVariable UUID fileId,
            HttpServletRequest request) {
        requireRoomMember(roomId, request);
        RoomFileService.DownloadableFile download =
                fileService.download(roomId, fileId);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(
                    download.file().getContentType());
        } catch (IllegalArgumentException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.file().getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        download.file().getOriginalFilename(),
                                        StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    private Device requireRoomMember(
            UUID roomId, HttpServletRequest request) {
        Device device = (Device) request.getAttribute("authenticatedDevice");
        if (device == null) {
            throw new RoomService.UnauthorizedRoomAccessException();
        }
        if (!device.getRoomId().equals(roomId)) {
            throw new RoomService.UnauthorizedRoomAccessException();
        }
        return device;
    }
}
