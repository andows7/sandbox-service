package com.huixue.sandbox.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huixue.sandbox.api.dto.SandboxExecuteRequest;
import com.huixue.sandbox.api.dto.SandboxExecuteResponse;
import com.huixue.sandbox.api.dto.TestCaseDto;
import com.huixue.sandbox.common.enums.JudgeStatus;
import com.huixue.sandbox.domain.service.SandboxExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SandboxController.class, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class SandboxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SandboxExecutionService executionService;

    @Test
    void testExecuteCodeSuccess() throws Exception {
        SandboxExecuteRequest request = new SandboxExecuteRequest();
        request.setSubmitId("sub-mock");
        request.setLanguage("JAVA");
        request.setCode("class Main{}");
        TestCaseDto tc = new TestCaseDto();
        tc.setInput("1");
        tc.setExpectedOutput("1");
        request.setTestCases(Collections.singletonList(tc));

        SandboxExecuteResponse response = new SandboxExecuteResponse();
        response.setStatus(JudgeStatus.AC);

        Mockito.when(executionService.execute(any(SandboxExecuteRequest.class))).thenReturn(response);

        mockMvc.perform(post("/v1/api/sandbox/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-Trace-Id", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("AC"));
    }

    @Test
    void testExecuteCodeValidationError() throws Exception {
        SandboxExecuteRequest request = new SandboxExecuteRequest();
        // Missing submitId and language to trigger validation error
        request.setCode("class Main{}");

        mockMvc.perform(post("/v1/api/sandbox/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
