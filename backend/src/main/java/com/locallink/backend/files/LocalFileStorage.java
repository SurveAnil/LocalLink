package com.locallink.backend.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(
            @Value("${locallink.files.storage-directory:./data/files}")
            String storageDirectory) {
        this.root = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID roomId, UUID fileId, InputStream content)
            throws IOException {
        String key = roomId + "/" + fileId;
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "upload-", ".tmp");
        try {
            Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return key;
    }

    @Override
    public Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage reference.");
        }
        return resolved;
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }
}
