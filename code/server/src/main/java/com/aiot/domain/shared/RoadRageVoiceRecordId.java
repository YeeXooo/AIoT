package com.aiot.domain.shared;

import java.util.Objects;

/**
 * 路怒语音存证标识。
 * 对应聚合根 RoadRageVoiceRecord (AR-05)。
 */
public record RoadRageVoiceRecordId(String id) {

    public RoadRageVoiceRecordId {
        Objects.requireNonNull(id, "id must not be null");
    }

    public static RoadRageVoiceRecordId generate() {
        return new RoadRageVoiceRecordId(AggregateId.generate(AggregateType.ROAD_RAGE_VOICE_RECORD).value());
    }
}
