package com.aiot.interfaces.rest;

import com.aiot.infra.storage.LocalFileStorageService;
import com.aiot.infra.storage.StorageProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    @Mock
    private LocalFileStorageService storageService;

    private StorageProperties props;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        props = new StorageProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(new StorageController(storageService, props)).build();
    }

    @Test
    void infoShouldReturnStorageProperties() throws Exception {
        props.setBasePath("/custom/base");
        props.setMaxVoiceFileSizeMb(512);
        props.setMaxOtaFileSizeMb(2048);
        props.setVoiceExpiryDays(60);

        mockMvc.perform(get("/api/v1/storage/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePath").value("/custom/base"))
                .andExpect(jsonPath("$.maxVoiceFileSizeMb").value(512))
                .andExpect(jsonPath("$.maxOtaFileSizeMb").value(2048))
                .andExpect(jsonPath("$.voiceExpiryDays").value(60));
    }

    @Test
    void infoShouldReturnDefaultValuesWhenNotConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/storage/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePath").value("/data/aiot"))
                .andExpect(jsonPath("$.maxVoiceFileSizeMb").value(256))
                .andExpect(jsonPath("$.maxOtaFileSizeMb").value(1024))
                .andExpect(jsonPath("$.voiceExpiryDays").value(30));
    }

    @Test
    void listShouldReturnFilesForDefaultDir() throws Exception {
        when(storageService.listFiles("voice")).thenReturn(List.of("file1.wav", "file2.wav"));

        mockMvc.perform(get("/api/v1/storage/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("file1.wav"))
                .andExpect(jsonPath("$[1]").value("file2.wav"));

        verify(storageService).listFiles("voice");
    }

    @Test
    void listShouldReturnFilesForCustomDir() throws Exception {
        when(storageService.listFiles("ota")).thenReturn(List.of("firmware.bin"));

        mockMvc.perform(get("/api/v1/storage/list").param("dir", "ota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("firmware.bin"));

        verify(storageService).listFiles("ota");
    }

    @Test
    void listShouldReturnEmptyArrayWhenDirIsEmpty() throws Exception {
        when(storageService.listFiles("voice")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/storage/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listShouldPropagateIOException() throws Exception {
        when(storageService.listFiles(anyString())).thenThrow(new IOException("disk full"));

        assertThrows(Exception.class,
                () -> mockMvc.perform(get("/api/v1/storage/list")));
    }

    @Test
    void uploadShouldStoreAndReturnPath() throws Exception {
        byte[] data = "test data".getBytes();

        mockMvc.perform(post("/api/v1/storage/upload")
                        .param("dir", "voice")
                        .param("fileName", "test.wav")
                        .content(data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stored"))
                .andExpect(jsonPath("$.path").value("voice/test.wav"));

        verify(storageService).store("voice", "test.wav", data);
    }

    @Test
    void uploadShouldStoreBinaryDataAndReturnPath() throws Exception {
        byte[] data = {0x01, 0x02, 0x03, 0x04};

        mockMvc.perform(post("/api/v1/storage/upload")
                        .param("dir", "reports")
                        .param("fileName", "binary.dat")
                        .content(data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stored"))
                .andExpect(jsonPath("$.path").value("reports/binary.dat"));

        verify(storageService).store("reports", "binary.dat", data);
    }

    @Test
    void uploadShouldPropagateIOException() throws Exception {
        doThrow(new IOException("permission denied")).when(storageService)
                .store(eq("ota"), eq("firmware.bin"), any(byte[].class));

        assertThrows(Exception.class,
                () -> mockMvc.perform(post("/api/v1/storage/upload")
                        .param("dir", "ota")
                        .param("fileName", "firmware.bin")
                        .content("payload".getBytes())));
    }
}
