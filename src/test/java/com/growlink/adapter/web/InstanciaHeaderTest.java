package com.growlink.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// el header X-Instancia es lo que permite ver el balanceo entre replicas
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstanciaHeaderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void todaRespuestaDiceQueInstanciaLaAtendio() throws Exception {
        mockMvc.perform(get("/api/perfil/me"))
                .andExpect(status().is(401))
                .andExpect(header().exists("X-Instancia"));
    }
}
