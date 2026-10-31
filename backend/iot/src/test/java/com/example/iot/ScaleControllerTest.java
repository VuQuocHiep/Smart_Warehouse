package com.example.iot;

import com.example.iot.controller.ScaleController;
import com.example.iot.exception.StudentException;
import com.example.iot.service.ScaleService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ScaleControllerTest {
    ScaleService service = mock(ScaleService.class);
    ScaleController controller = new ScaleController(service);
    MockMvc mvc;
    String body = """
        {"deviceCode":"ESP_ENV_01","weightGrams":100,"grossGrams":120,"stable":true,
         "calibrated":true,"tared":true,"overloaded":false,"status":"READY"}
        """;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(controller, "ingestKey", "test-key");
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new StudentException()).build();
    }
    @Test void deviceIngestionRequiresCorrectNonEmptyKey() throws Exception {
        for (String key : new String[]{"", "wrong"}) mvc.perform(post("/api/scale/readings")
            .header("X-Device-Key", key).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        mvc.perform(post("/api/scale/readings").header("X-Device-Key", "test-key")
            .contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(content().json("{}"));
        verify(service).receive(any());
    }
}
