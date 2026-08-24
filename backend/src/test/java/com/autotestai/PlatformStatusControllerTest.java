package com.autotestai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.autotestai.config.SecurityConfig;
import com.autotestai.security.JsonAccessDeniedHandler;
import com.autotestai.security.JsonAuthenticationEntryPoint;

@WebMvcTest(PlatformStatusController.class)
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class PlatformStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootReturnsPlatformStatus() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("AutoTest AI"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.stage").value("release-candidate"))
                .andExpect(jsonPath("$.environments")
                        .value("/api/projects/{projectId}/environments"))
                .andExpect(jsonPath("$.apis")
                        .value("/api/projects/{projectId}/apis"))
                .andExpect(jsonPath("$.testCases")
                        .value("/api/projects/{projectId}/apis/{apiId}/test-cases"))
                .andExpect(jsonPath("$.testSuites")
                        .value("/api/projects/{projectId}/test-suites"))
                .andExpect(jsonPath("$.runTestCase")
                        .value("/api/projects/{projectId}/apis/{apiId}/test-cases/{testCaseId}/runs"))
                .andExpect(jsonPath("$.runTestSuite")
                        .value("/api/projects/{projectId}/test-suites/{suiteId}/runs"))
                .andExpect(jsonPath("$.testRun")
                        .value("/api/projects/{projectId}/test-runs/{runId}"))
                .andExpect(jsonPath("$.testReports")
                        .value("/api/projects/{projectId}/test-reports"))
                .andExpect(jsonPath("$.dashboard").value("/api/dashboard/summary"))
                .andExpect(jsonPath("$.aiStatus").value("/api/ai/status"))
                .andExpect(jsonPath("$.aiGenerate")
                        .value("/api/projects/{projectId}/apis/{apiId}/ai/test-cases/generate"))
                .andExpect(jsonPath("$.aiDiagnosis")
                        .value("/api/projects/{projectId}/test-runs/{runId}/results/{resultId}/ai/diagnosis"))
                .andExpect(jsonPath("$.health").value("/actuator/health"));
    }
}
