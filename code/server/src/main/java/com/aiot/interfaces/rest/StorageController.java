package com.aiot.interfaces.rest;

import com.aiot.infra.storage.LocalFileStorageService;
import com.aiot.infra.storage.StorageProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {

    private final LocalFileStorageService storage;
    private final StorageProperties props;

    public StorageController(LocalFileStorageService storage,
                              StorageProperties props) {
        this.storage = storage;
        this.props = props;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "basePath", props.getBasePath(),
                "maxVoiceFileSizeMb", props.getMaxVoiceFileSizeMb(),
                "maxOtaFileSizeMb", props.getMaxOtaFileSizeMb(),
                "voiceExpiryDays", props.getVoiceExpiryDays()
        );
    }

    @GetMapping("/list")
    public List<String> list(@RequestParam(defaultValue = "voice") String dir)
            throws IOException {
        return storage.listFiles(dir);
    }

    @PostMapping("/upload")
    public Map<String, String> upload(@RequestParam String dir,
                                       @RequestParam String fileName,
                                       @RequestBody byte[] data)
            throws IOException {
        storage.store(dir, fileName, data);
        return Map.of("status", "stored", "path", dir + "/" + fileName);
    }
}
