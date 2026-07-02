package com.aiot.infra.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.stream.Stream;

@Component
public class FileExpiryService {

    private static final Logger log =
            LoggerFactory.getLogger(FileExpiryService.class);

    private final StorageProperties props;

    public FileExpiryService(StorageProperties props) {
        this.props = props;
    }

    public void cleanExpiredVoiceFiles() throws IOException {
        Path voiceDir = Paths.get(props.getBasePath(), "voice");
        if (!Files.exists(voiceDir)) { return; }

        long expiryMillis = props.getVoiceExpiryDays() * 24 * 60 * 60 * 1000L;
        Instant cutoff = Instant.now().minusMillis(expiryMillis);

        try (Stream<Path> stream = Files.list(voiceDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p)
                                    .toInstant().isBefore(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            log.info("Expired voice file deleted: {}", p);
                        } catch (IOException e) {
                            log.warn("Failed to delete expired file: {}", p);
                        }
                    });
        }
    }
}
