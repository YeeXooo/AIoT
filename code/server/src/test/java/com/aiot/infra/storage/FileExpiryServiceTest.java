package com.aiot.infra.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class FileExpiryServiceTest {

    @TempDir
    Path tempDir;

    private FileExpiryService service;
    private StorageProperties props;

    @BeforeEach
    void setUp() {
        props = new StorageProperties();
        props.setBasePath(tempDir.toString());
        props.setVoiceExpiryDays(30);
        service = new FileExpiryService(props);
    }

    @Test
    void cleanExpiredVoiceFiles_shouldDeleteExpiredFiles() throws IOException {
        Path voiceDir = tempDir.resolve("voice");
        Files.createDirectories(voiceDir);
        Path oldFile = voiceDir.resolve("old.wav");
        Files.writeString(oldFile, "old");
        Files.setLastModifiedTime(oldFile, FileTime.from(Instant.now().minus(60, ChronoUnit.DAYS)));

        service.cleanExpiredVoiceFiles();

        assertFalse(Files.exists(oldFile));
    }

    @Test
    void cleanExpiredVoiceFiles_shouldKeepRecentFiles() throws IOException {
        Path voiceDir = tempDir.resolve("voice");
        Files.createDirectories(voiceDir);
        Path recentFile = voiceDir.resolve("recent.wav");
        Files.writeString(recentFile, "recent");
        Files.setLastModifiedTime(recentFile, FileTime.from(Instant.now().minus(5, ChronoUnit.DAYS)));

        service.cleanExpiredVoiceFiles();

        assertTrue(Files.exists(recentFile));
    }

    @Test
    void cleanExpiredVoiceFiles_shouldNotThrowWhenDirectoryDoesNotExist() {
        assertDoesNotThrow(() -> service.cleanExpiredVoiceFiles());
    }

    @Test
    void cleanExpiredVoiceFiles_shouldSkipDirectories() throws IOException {
        Path voiceDir = tempDir.resolve("voice");
        Files.createDirectories(voiceDir);
        Path subDir = voiceDir.resolve("subdir");
        Files.createDirectory(subDir);

        assertDoesNotThrow(() -> service.cleanExpiredVoiceFiles());
        assertTrue(Files.exists(subDir));
    }

    @Test
    void cleanExpiredVoiceFiles_withZeroExpiryDays_shouldDeleteAll() throws IOException {
        props.setVoiceExpiryDays(0);
        service = new FileExpiryService(props);

        Path voiceDir = tempDir.resolve("voice");
        Files.createDirectories(voiceDir);
        Path file = voiceDir.resolve("test.wav");
        Files.writeString(file, "data");

        service.cleanExpiredVoiceFiles();

        assertFalse(Files.exists(file));
    }

    @Test
    void cleanExpiredVoiceFiles_withVeryLargeExpiryDays_shouldKeepAll() throws IOException {
        props.setVoiceExpiryDays(9999);
        service = new FileExpiryService(props);

        Path voiceDir = tempDir.resolve("voice");
        Files.createDirectories(voiceDir);
        Path file = voiceDir.resolve("keep.wav");
        Files.writeString(file, "data");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(365, ChronoUnit.DAYS)));

        service.cleanExpiredVoiceFiles();

        assertTrue(Files.exists(file));
    }
}
