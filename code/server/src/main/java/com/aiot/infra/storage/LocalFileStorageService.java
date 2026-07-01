package com.aiot.infra.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LocalFileStorageService {

    private static final Logger log =
            LoggerFactory.getLogger(LocalFileStorageService.class);

    private final StorageProperties props;

    public LocalFileStorageService(StorageProperties props) {
        this.props = props;
        ensureDirectories();
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(Paths.get(props.getBasePath(), "voice"));
            Files.createDirectories(Paths.get(props.getBasePath(), "ota"));
            Files.createDirectories(Paths.get(props.getBasePath(), "reports"));
        } catch (IOException e) {
            log.error("Failed to create storage directories", e);
        }
    }

    public void store(String subDir, String fileName, byte[] data)
            throws IOException {
        Path dir = Paths.get(props.getBasePath(), subDir);
        Files.createDirectories(dir);
        Path filePath = dir.resolve(fileName);
        Files.write(filePath, data);
        log.info("File stored: {} ({} bytes)", filePath, data.length);
    }

    public byte[] read(String subDir, String fileName) throws IOException {
        Path filePath = Paths.get(props.getBasePath(), subDir, fileName);
        return Files.readAllBytes(filePath);
    }

    public boolean exists(String subDir, String fileName) {
        return Files.exists(Paths.get(props.getBasePath(), subDir, fileName));
    }

    public void delete(String subDir, String fileName) throws IOException {
        Path filePath = Paths.get(props.getBasePath(), subDir, fileName);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.info("File deleted: {}", filePath);
        }
    }

    public List<String> listFiles(String subDir) throws IOException {
        Path dir = Paths.get(props.getBasePath(), subDir);
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        }
    }
}
