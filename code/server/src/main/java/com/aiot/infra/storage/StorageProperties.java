package com.aiot.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aiot.storage")
public class StorageProperties {

    private String basePath = "/data/aiot";
    private long maxVoiceFileSizeMb = 256;
    private long maxOtaFileSizeMb = 1024;
    private long voiceExpiryDays = 30;

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public long getMaxVoiceFileSizeMb() {
        return maxVoiceFileSizeMb;
    }

    public void setMaxVoiceFileSizeMb(long maxVoiceFileSizeMb) {
        this.maxVoiceFileSizeMb = maxVoiceFileSizeMb;
    }

    public long getMaxOtaFileSizeMb() {
        return maxOtaFileSizeMb;
    }

    public void setMaxOtaFileSizeMb(long maxOtaFileSizeMb) {
        this.maxOtaFileSizeMb = maxOtaFileSizeMb;
    }

    public long getVoiceExpiryDays() {
        return voiceExpiryDays;
    }

    public void setVoiceExpiryDays(long voiceExpiryDays) {
        this.voiceExpiryDays = voiceExpiryDays;
    }
}
