package com.locallink.backend.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

public interface FileStorage {

    String store(UUID roomId, UUID fileId, InputStream content)
            throws IOException;

    Path resolve(String storageKey);

    void delete(String storageKey) throws IOException;
}
