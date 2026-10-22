package com.locallink.backend.exception;

import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.locallink.backend.room.RoomService;
import com.locallink.backend.files.RoomFileService;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoomService.RoomExpiredException.class)
    public ResponseEntity<Map<String, String>> handleRoomExpired(
            RoomService.RoomExpiredException ex) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "error", "ROOM_EXPIRED",
                "message", "This room has expired.",
                "expiresAt", ex.getExpiresAt().toString()));
    }

    @ExceptionHandler(RoomService.RoomClosedException.class)
    public ResponseEntity<Map<String, String>> handleRoomClosed(
            RoomService.RoomClosedException ex) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "error", "ROOM_CLOSED",
                "message", "This room is closed."));
    }

    @ExceptionHandler(RoomService.InvalidHostRecoveryException.class)
    public ResponseEntity<Map<String, String>> handleInvalidHostRecovery(
            RoomService.InvalidHostRecoveryException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "HOST_RECOVERY_INVALID",
                "message", "The host recovery credential is invalid."));
    }

    @ExceptionHandler(RoomService.HostRecoveryLockedException.class)
    public ResponseEntity<Map<String, String>> handleHostRecoveryLocked(
            RoomService.HostRecoveryLockedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "HOST_RECOVERY_LOCKED",
                "message", "Host recovery is temporarily locked. Try again later."));
    }

    @ExceptionHandler(RoomService.UnauthorizedRoomAccessException.class)
    public ResponseEntity<Map<String, String>> handleRoomAccessDenied(
            RoomService.UnauthorizedRoomAccessException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "ROOM_ACCESS_DENIED",
                "message", "You do not have access to this room."));
    }

    @ExceptionHandler(RoomService.RoomNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleRoomNotFound(
            RoomService.RoomNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "ROOM_NOT_FOUND",
                "message", "The requested room was not found."));
    }

    @ExceptionHandler(RoomFileService.FileTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handleFileTooLarge(
            RoomFileService.FileTooLargeException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "error", "FILE_TOO_LARGE",
                "message", ex.getMessage(),
                "maxSizeBytes", ex.getMaxSizeBytes()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMultipartTooLarge(
            MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "error", "FILE_TOO_LARGE",
                "message", "The uploaded file exceeds the configured request limit."));
    }

    @ExceptionHandler(RoomFileService.FileSharingDisabledException.class)
    public ResponseEntity<Map<String, String>> handleFileSharingDisabled(
            RoomFileService.FileSharingDisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "FILE_SHARING_DISABLED",
                "message", ex.getMessage()));
    }

    @ExceptionHandler(RoomFileService.FileNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleFileNotFound(
            RoomFileService.FileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "ROOM_FILE_NOT_FOUND",
                "message", "The requested file was not found in this room."));
    }

    @ExceptionHandler(RoomFileService.FileUploadConflictException.class)
    public ResponseEntity<Map<String, String>> handleFileUploadConflict(
            RoomFileService.FileUploadConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "UPLOAD_ID_CONFLICT",
                "message", ex.getMessage()));
    }

    @ExceptionHandler(RoomFileService.FileStorageException.class)
    public ResponseEntity<Map<String, String>> handleFileStorageFailure(
            RoomFileService.FileStorageException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "FILE_STORAGE_FAILED",
                "message", "The file could not be stored or retrieved."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(
            IllegalArgumentException ex) {
        String message = ex.getMessage() == null
                ? "Invalid request." : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "INVALID_REQUEST",
                "message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request.");

        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "VALIDATION_ERROR");
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}