package com.example.iot;

import com.example.iot.controller.ScaleController;
import com.example.iot.service.ScaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(ScaleAuthorizationTest.Config.class)
class ScaleAuthorizationTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean ScaleService service() { return mock(ScaleService.class); }
        @Bean ScaleController controller(ScaleService service) { return new ScaleController(service); }
    }
    @Autowired ScaleController controller;
    @Test @WithMockUser(roles = "STAFF") void staffCanTareButCannotCalibrateOrChangeTolerance() {
        assertDoesNotThrow(() -> controller.command(new ScaleController.CommandRequest("TARE", 0)));
        assertThrows(AccessDeniedException.class, () -> controller.command(new ScaleController.CommandRequest("CALIBRATE", 100)));
        assertThrows(AccessDeniedException.class, () -> controller.settings(new ScaleController.SettingsRequest(5, 2)));
    }
    @Test @WithMockUser(roles = "MANAGER") void managerCanCalibrateAndChangeTolerance() {
        assertDoesNotThrow(() -> controller.command(new ScaleController.CommandRequest("CALIBRATE", 100)));
        assertDoesNotThrow(() -> controller.settings(new ScaleController.SettingsRequest(5, 2)));
    }
    @Test @WithMockUser(roles = "GUEST") void otherRoleCannotReadScale() {
        assertThrows(AccessDeniedException.class, () -> controller.status());
    }
}
