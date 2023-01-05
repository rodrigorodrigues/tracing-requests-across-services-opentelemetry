package com.example.springboot;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "address-service-url=http://localhost:${wiremock.server.port}")
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
class SimpleRestApplicationTests {
    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        stubFor(WireMock.get(urlPathEqualTo("/v1/addresses/person/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"id\":\"1\",\"street\":\"Something\"}")));
    }

    @Test
    void contextLoads() throws Exception {
        mockMvc.perform(get("/v1/persons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..id", hasSize(2)));

        mockMvc.perform(get("/v1/persons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test"))
                .andExpect(jsonPath("$.address.street").value("Something"));

        mockMvc.perform(get("/v1/persons/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Anonymous"))
                .andExpect(jsonPath("$.address").doesNotHaveJsonPath());

        mockMvc.perform(get("/v1/persons/3"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/v1/persons")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("New"));

        mockMvc.perform(post("/v1/persons")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v1/persons/3"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/v1/persons/3")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Update\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Update"));

        mockMvc.perform(delete("/v1/persons/3"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/persons/3"))
                .andExpect(status().isNotFound());
    }

}
