package com.vswitch.watermeter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TestStoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TestStoreService testStoreService;

    @Test
    void storeTestIdReturnsOk() throws Exception {
        mockMvc.perform(put("/test/hello-dynamo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.test_id").value("hello-dynamo"))
                .andExpect(jsonPath("$.status").value("stored"));

        verify(testStoreService).storeTestId("hello-dynamo");
    }
}
