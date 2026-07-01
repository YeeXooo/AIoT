package com.aiot.infra.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 数据脱敏校验器。
 * <p>
 * 校验数据体中不包含原始图像字段（raw_image）。
 * 在数据上云 MQTT 通道之前执行校验。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.1
 * </p>
 */
@Component
public class DataDesensitizationValidator {

    private static final Logger log = LoggerFactory.getLogger(DataDesensitizationValidator.class);

    /**
     * 禁止的字段名集合。
     */
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "raw_image",
            "raw_frame",
            "original_image",
            "face_image"
    );

    /**
     * 校验数据是否已脱敏。
     *
     * @param data 数据体（Map 格式）
     * @return 校验结果
     */
    public ValidationResult validate(Map<String, Object> data) {
        if (data == null) {
            return ValidationResult.success();
        }

        for (String forbiddenField : FORBIDDEN_FIELDS) {
            if (data.containsKey(forbiddenField)) {
                String message = String.format("Data contains forbidden field: %s", forbiddenField);
                log.warn("Desensitization validation failed: {}", message);
                return ValidationResult.failure(message, forbiddenField);
            }
        }

        // 递归检查嵌套对象
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) entry.getValue();
                ValidationResult nestedResult = validate(nested);
                if (!nestedResult.isSuccess()) {
                    return nestedResult;
                }
            }
        }

        return ValidationResult.success();
    }

    /**
     * 校验 JSON 字符串是否已脱敏。
     *
     * @param json JSON 字符串
     * @return 校验结果
     */
    public ValidationResult validateJson(String json) {
        if (json == null || json.isEmpty()) {
            return ValidationResult.success();
        }

        for (String forbiddenField : FORBIDDEN_FIELDS) {
            if (json.contains("\"" + forbiddenField + "\"")) {
                String message = String.format("JSON contains forbidden field: %s", forbiddenField);
                log.warn("Desensitization validation failed: {}", message);
                return ValidationResult.failure(message, forbiddenField);
            }
        }

        return ValidationResult.success();
    }

    /**
     * 校验结果。
     *
     * @param isSuccess      是否成功
     * @param message        结果消息
     * @param violatedField  违规字段
     */
    record ValidationResult(boolean isSuccess, String message, String violatedField) {

        static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        static ValidationResult failure(String message, String violatedField) {
            return new ValidationResult(false, message, violatedField);
        }
    }
}
