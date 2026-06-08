package com.vswitch.watermeter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void listAllTestIdsReturnsArray() throws Exception {
        when(testStoreService.listAllTestIds()).thenReturn(List.of("alpha", "beta"));

        mockMvc.perform(get("/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("alpha"))
                .andExpect(jsonPath("$[1]").value("beta"));

        verify(testStoreService).listAllTestIds();
    }
}
