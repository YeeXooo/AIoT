package com.aiot.interfaces.rest;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drivers/{driverId}/perception")
public class PerceptionController {

    private static long counter = 0;

    @GetMapping("/latest")
    public Map<String, Object> getLatest(@PathVariable String driverId) {
        counter++;
        double faceConf = 0.75 + 0.20 * Math.sin(counter * 0.3);
        boolean fatigued = counter % 15 < 5;
        boolean distracted = counter % 20 > 15;

        return Map.of(
                "driverId", driverId,
                "timestamp", System.currentTimeMillis(),
                "faceDetected", faceConf > 0.60,
                "faceConfidence", Math.round(faceConf * 100.0) / 100.0,
                "fatigueLevel", fatigued ? "L2" : "NONE",
                "fatigueDesc", fatigued ? "频繁眨眼" : "正常",
                "distractionLevel", distracted ? "L2" : "NONE",
                "distractionDesc", distracted ? "视线偏离" : "专注",
                "emotion", new String[]{"平静", "焦躁", "正常", "正常", "平静"}[(int)(counter % 5)],
                "handsOnWheel", counter % 7 != 0
        );
    }
}
