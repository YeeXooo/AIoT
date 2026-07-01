package com.aiot.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataDesensitizationValidator 单元测试。
 */
class DataDesensitizationValidatorTest {

    private DataDesensitizationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DataDesensitizationValidator();
    }

    @Test
    void validate_cleanData_shouldReturnSuccess() {
        Map<String, Object> data = new HashMap<>();
        data.put("heart_rate", 75);
        data.put("blood_oxygen", 98);

        DataDesensitizationValidator.ValidationResult result = validator.validate(data);
        assertTrue(result.isSuccess());
    }

    @Test
    void validate_withRawImage_shouldReturnFailure() {
        Map<String, Object> data = new HashMap<>();
        data.put("heart_rate", 75);
        data.put("raw_image", "base64data");

        DataDesensitizationValidator.ValidationResult result = validator.validate(data);
        assertFalse(result.isSuccess());
        assertEquals("raw_image", result.violatedField());
    }

    @Test
    void validate_withNestedRawImage_shouldReturnFailure() {
        Map<String, Object> data = new HashMap<>();
        data.put("heart_rate", 75);

        Map<String, Object> nested = new HashMap<>();
        nested.put("raw_image", "base64data");
        data.put("sensor_data", nested);

        DataDesensitizationValidator.ValidationResult result = validator.validate(data);
        assertFalse(result.isSuccess());
        assertEquals("raw_image", result.violatedField());
    }

    @Test
    void validate_withNullData_shouldReturnSuccess() {
        DataDesensitizationValidator.ValidationResult result = validator.validate(null);
        assertTrue(result.isSuccess());
    }

    @Test
    void validateJson_cleanJson_shouldReturnSuccess() {
        String json = "{\"heart_rate\": 75, \"blood_oxygen\": 98}";

        DataDesensitizationValidator.ValidationResult result = validator.validateJson(json);
        assertTrue(result.isSuccess());
    }

    @Test
    void validateJson_withRawImage_shouldReturnFailure() {
        String json = "{\"heart_rate\": 75, \"raw_image\": \"base64data\"}";

        DataDesensitizationValidator.ValidationResult result = validator.validateJson(json);
        assertFalse(result.isSuccess());
        assertEquals("raw_image", result.violatedField());
    }

    @Test
    void validateJson_withNullJson_shouldReturnSuccess() {
        DataDesensitizationValidator.ValidationResult result = validator.validateJson(null);
        assertTrue(result.isSuccess());
    }

    @Test
    void validateJson_withEmptyJson_shouldReturnSuccess() {
        DataDesensitizationValidator.ValidationResult result = validator.validateJson("");
        assertTrue(result.isSuccess());
    }
}
