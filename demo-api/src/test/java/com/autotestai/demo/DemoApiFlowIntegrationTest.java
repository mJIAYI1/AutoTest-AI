package com.autotestai.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApiFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesImportableOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/login']").exists())
                .andExpect(jsonPath("$.paths['/orders']").exists());
    }

    @Test
    void demoUserCanLoginAndReadProfile() throws Exception {
        String loginResponse = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"demo","password":"demo123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = loginResponse.substring(
                loginResponse.indexOf("demo-token-"),
                loginResponse.indexOf("\",\"user\""));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"));
    }

    @Test
    void deliberatelyAcceptsNegativePriceAndZeroQuantity() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Broken Price Product","price":-1.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(-1.0));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"quantity":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(0));
    }

    @Test
    void deliberatelyReturnsInternalErrorForInvalidTokenAndMissingUser() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTENTIONAL_DEMO_BUG"));

        mockMvc.perform(get("/users/999999"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.intentionalBug").value(true));
    }
}
