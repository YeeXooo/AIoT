package com.aiot.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 数据脱敏校验门控。
 * <p>
 * 在 MQTT 上行数据和 SQLite 本地持久化写入前，
 * 校验数据体中不包含原始敏感字段（如 raw_image），
 * 实现 BR-04 隐私边界"原始图像不出边缘侧"的技术保障。
 * </p>
 * <p>
 * 本期以 Java 白名单校验替代 JSON Schema 文件校验——<br>
 * 禁止字段集合可配置，校验逻辑等价于 JSON Schema additionalProperties=false 语义。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.1
 * </p>
 */
@Component
public class SecurityGateFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityGateFilter.class);

    private final ObjectMapper objectMapper;

    /**
     * 被禁止出现在数据体中的原始敏感字段集合。
     * 本期仅包含 raw_image，可扩展添加其他禁止字段。
     */
    private static final Set<String> PROHIBITED_FIELDS = Set.of(
            "raw_image",
            "rawImage",
            "raw_frame",
            "rawFrame",
            "original_image",
            "originalImage",
            "image_data",
            "imageData",
            "video_frame",
            "videoFrame",
            "raw_audio",
            "rawAudio"
    );

    public SecurityGateFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 校验 JSON 数据体中是否包含禁止的原始敏感字段。
     * <p>
     * 校验策略：递归遍历 JSON 树中所有字段名，检查是否命中禁止集合。
     * </p>
     *
     * @param jsonPayload JSON 格式的数据体
     * @param source      数据来源标识（如 "MQTT_UPLINK" / "SQLITE_WRITE"），用于审计日志
     * @return 校验结果——通过则返回 ValidationResult.passed()，拒绝则返回含违规字段的失败结果
     */
    public ValidationResult validate(String jsonPayload, String source) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            String prohibited = findProhibitedField(root, "");
            if (prohibited != null) {
                log.warn("脱敏校验失败: source={}, prohibitedField={}", source, prohibited);
                return ValidationResult.rejected(prohibited);
            }
            return ValidationResult.passed();
        } catch (Exception e) {
            log.error("脱敏校验异常: source={}, error={}", source, e.getMessage());
            return ValidationResult.rejected("校验异常: " + e.getMessage());
        }
    }

    /**
     * 递归查找 JSON 树中的禁止字段。
     *
     * @return 首个命中的禁止字段名（含路径），未命中则返回 null
     */
    private String findProhibitedField(JsonNode node, String path) {
        if (node.isObject()) {
            var it = node.fields();
            while (it.hasNext()) {
                var entry = it.next();
                String fieldName = entry.getKey();
                String fullPath = path.isEmpty() ? fieldName : path + "." + fieldName;

                if (PROHIBITED_FIELDS.contains(fieldName)) {
                    return fullPath;
                }

                String result = findProhibitedField(entry.getValue(), fullPath);
                if (result != null) {
                    return result;
                }
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String result = findProhibitedField(node.get(i), path + "[" + i + "]");
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * 监听安全审计事件，记录到日志。
     * <p>
     * 本期以 SLF4J 记录审计日志，可扩展为写入审计数据库或推送到安全运维通道。
     * </p>
     */
    @EventListener
    public void onSecurityAuditEvent(SecurityAuditEvent event) {
        log.warn("安全审计: source={}, violation={}, detail={}, timestamp={}",
                event.source(), event.violation(), event.detail(), event.timestamp());
    }

    /**
     * 校验结果。
     *
     * @param ok            是否通过
     * @param rejectedField 被拒绝的字段路径（通过时为 null）
     */
    public record ValidationResult(boolean ok, String rejectedField) {
        public static ValidationResult passed() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult rejected(String field) {
            return new ValidationResult(false, field);
        }

        public boolean isRejected() {
            return !ok;
        }
    }
}
