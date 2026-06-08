package com.vswitch.watermeter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TestLambdaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testLambdaReturnsHelloWorld() throws Exception {
        mockMvc.perform(get("/testlamda"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello world"));
    }
}
