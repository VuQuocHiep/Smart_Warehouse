package com.example.iot;

import com.example.iot.controller.ShelfEnvironmentController;
import com.example.iot.entity.WarehouseEnvironmentReading;
import com.example.iot.exception.StudentException;
import com.example.iot.repository.EnvironmentAlertReceiptRepository;
import com.example.iot.repository.ProductEnvironmentAlertRepository;
import com.example.iot.service.ShelfEnvironmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SensorIngestionControllerTest {
    ShelfEnvironmentService service = mock(ShelfEnvironmentService.class);
    ShelfEnvironmentController controller = new ShelfEnvironmentController(service,
            mock(ProductEnvironmentAlertRepository.class), mock(EnvironmentAlertReceiptRepository.class));
    MockMvc mvc;
    String body = """
        {"deviceCode":"ESP_ENV_01","temperature":31.6,"humidity":69.1,"gas":339,"gasTvocPpb":42}
        """;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(controller, "ingestKey", "test-device-secret");
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new StudentException()).build();
    }

    @Test void returnsModelCommandAndConfidenceToAuthenticatedDevice() throws Exception {
        var result = new WarehouseEnvironmentReading();
        result.setDeviceCode("ESP_ENV_01");
        result.setRiskLevel(2);
        result.setConfidence(.91);
        result.setActionCode("RED_LED_FAN");
        result.setPredictionStatus("READY");
        when(service.receive(any())).thenReturn(result);
        mvc.perform(post("/api/sensor/readings").header("X-Device-Key", "test-device-secret")
                .contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.riskLevel").value(2))
            .andExpect(jsonPath("$.controlAllowed").value(true))
            .andExpect(jsonPath("$.confidence").value(.91))
            .andExpect(jsonPath("$.actionCode").value("RED_LED_FAN"));
        verify(service).receive(new ShelfEnvironmentService.Reading("ESP_ENV_01", 31.6, 69.1, 339d, 42d));
    }

    @Test void rejectsMissingOrWrongKeyAndDisabledIngestion() throws Exception {
        for (String key : new String[]{"", "wrong"}) {
            mvc.perform(post("/api/sensor/readings").header("X-Device-Key", key)
                    .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        }
        ReflectionTestUtils.setField(controller, "ingestKey", "");
        mvc.perform(post("/api/sensor/readings").header("X-Device-Key", "test-device-secret")
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void rejectsMissingDeviceAndInvalidHumidity() throws Exception {
        for (String invalid : new String[]{body.replace("ESP_ENV_01", ""), body.replace("69.1", "101")}) {
            mvc.perform(post("/api/sensor/readings").header("X-Device-Key", "test-device-secret")
                    .contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
