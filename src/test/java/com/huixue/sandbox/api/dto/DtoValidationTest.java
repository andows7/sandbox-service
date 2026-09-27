package com.huixue.sandbox.api.dto;

import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.common.enums.LanguageType;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DtoValidationTest {

    private final Validator validator;

    public DtoValidationTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidRequest() {
        SandboxExecuteRequest request = new SandboxExecuteRequest();
        request.setSubmitId("sub-123");
        request.setLanguage("JAVA");
        request.setCode("public class Main {}");
        request.setTimeLimitMs(1000);
        request.setMemoryLimitMb(128);

        Set<ConstraintViolation<SandboxExecuteRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Should have no validation errors");
    }

    @Test
    void testInvalidSubmitId() {
        SandboxExecuteRequest request = new SandboxExecuteRequest();
        request.setSubmitId("");
        request.setLanguage("JAVA");
        request.setCode("public class Main {}");

        Set<ConstraintViolation<SandboxExecuteRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("不能为空")));
    }

    @Test
    void testEnums() {
        JudgeStatus status = JudgeStatus.valueOf("AC");
        assertEquals(JudgeStatus.AC, status);

        LanguageType lang = LanguageType.valueOf("JAVA");
        assertEquals(LanguageType.JAVA, lang);
    }
}
