package com.example.iot;

import com.example.iot.controller.DemandForecastController;
import com.example.iot.service.DemandForecastService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.iot.exception.StudentException;

@SpringJUnitConfig(DemandForecastAuthorizationTest.Config.class)
class DemandForecastAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean DemandForecastService service() { return mock(DemandForecastService.class); }
        @Bean com.example.iot.service.DemandJobService jobs() { return mock(com.example.iot.service.DemandJobService.class); }
        @Bean com.example.iot.service.ReplenishmentService replenishment() { return mock(com.example.iot.service.ReplenishmentService.class); }
        @Bean DemandForecastController controller(DemandForecastService service,com.example.iot.service.DemandJobService jobs,com.example.iot.service.ReplenishmentService replenishment) { return new DemandForecastController(service,jobs,replenishment); }
    }
    @Autowired DemandForecastController controller;

    @Test @WithMockUser(roles = "STAFF")
    void staffCannotReadForecastsOrStartTraining() {
        assertThrows(AccessDeniedException.class, controller::forecasts);
        assertThrows(AccessDeniedException.class, controller::start);
        assertThrows(AccessDeniedException.class, controller::replenishment);
        assertThrows(AccessDeniedException.class, controller::status);
    }

    @Test @WithMockUser(roles = "MANAGER")
    void managerCanReadCatalog() { assertDoesNotThrow(controller::catalog); }

    @Test @WithMockUser(roles = "ADMIN")
    void adminCanStartDemo() { assertEquals(202, controller.start().getStatusCode().value()); }

    @Test @WithMockUser(roles = "STAFF")
    void forbiddenIsHttp403EvenWithExistingGlobalAdvice() throws Exception {
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new StudentException()).build()
                .perform(get("/api/demand/forecasts")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }
}
