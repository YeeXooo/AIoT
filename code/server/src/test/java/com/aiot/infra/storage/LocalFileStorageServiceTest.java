package com.aiot.infra.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService service;
    private StorageProperties props;

    @BeforeEach
    void setUp() {
        props = new StorageProperties();
        props.setBasePath(tempDir.toString());
        service = new LocalFileStorageService(props);
    }

    @Test
    void store_shouldWriteFileToSubdirectory() throws IOException {
        byte[] data = "test-content".getBytes();

        service.store("voice", "recording1.wav", data);

        Path expectedFile = tempDir.resolve("voice").resolve("recording1.wav");
        assertTrue(Files.exists(expectedFile));
        assertArrayEquals(data, Files.readAllBytes(expectedFile));
    }

    @Test
    void read_shouldReturnFileContents() throws IOException {
        byte[] data = "hello-world".getBytes();
        service.store("voice", "file1.dat", data);

        byte[] result = service.read("voice", "file1.dat");

        assertArrayEquals(data, result);
    }

    @Test
    void read_shouldThrowWhenFileDoesNotExist() {
        assertThrows(IOException.class,
                () -> service.read("voice", "nonexistent.dat"));
    }

    @Test
    void exists_shouldReturnTrueForExistingFile() throws IOException {
        service.store("voice", "existing.dat", "data".getBytes());

        assertTrue(service.exists("voice", "existing.dat"));
    }

    @Test
    void exists_shouldReturnFalseForNonexistentFile() {
        assertFalse(service.exists("voice", "nonexistent.dat"));
    }

    @Test
    void delete_shouldRemoveFile() throws IOException {
        service.store("voice", "temp.dat", "data".getBytes());
        assertTrue(service.exists("voice", "temp.dat"));

        service.delete("voice", "temp.dat");

        assertFalse(service.exists("voice", "temp.dat"));
    }

    @Test
    void delete_shouldNotThrowWhenFileDoesNotExist() {
        assertDoesNotThrow(() -> service.delete("voice", "nonexistent.dat"));
    }

    @Test
    void listFiles_shouldReturnFileNames() throws IOException {
        service.store("voice", "a.wav", new byte[1]);
        service.store("voice", "b.wav", new byte[1]);

        List<String> files = service.listFiles("voice");

        assertEquals(2, files.size());
        assertTrue(files.contains("a.wav"));
        assertTrue(files.contains("b.wav"));
    }

    @Test
    void listFiles_shouldReturnEmptyListForNonexistentDirectory() throws IOException {
        List<String> files = service.listFiles("nonexistent");

        assertTrue(files.isEmpty());
    }

    @Test
    void store_inOtaSubdirectory_shouldWork() throws IOException {
        byte[] data = new byte[1024];
        service.store("ota", "v2.0.0.bin", data);

        assertTrue(service.exists("ota", "v2.0.0.bin"));
    }

    @Test
    void store_inReportsSubdirectory_shouldWork() throws IOException {
        byte[] data = "report".getBytes();
        service.store("reports", "report-1.json", data);

        assertTrue(service.exists("reports", "report-1.json"));
    }

    @Test
    void store_emptyData_shouldWork() throws IOException {
        service.store("voice", "empty.dat", new byte[0]);

        byte[] result = service.read("voice", "empty.dat");
        assertEquals(0, result.length);
    }

    @Test
    void constructor_shouldEnsureDirectoriesExist() {
        assertTrue(Files.exists(tempDir.resolve("voice")));
        assertTrue(Files.exists(tempDir.resolve("ota")));
        assertTrue(Files.exists(tempDir.resolve("reports")));
    }

    @Test
    void store_overwriteExisting_shouldReplace() throws IOException {
        service.store("voice", "override.dat", "old".getBytes());
        service.store("voice", "override.dat", "new-data".getBytes());

        byte[] result = service.read("voice", "override.dat");
        assertEquals("new-data", new String(result));
    }
}
