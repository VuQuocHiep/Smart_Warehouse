package com.example.iot.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HybridRiskServiceTest {
    @Test void exactGasThresholdsRaiseFinalLevelWithoutChangingAiInputs() {
        var model = mock(TemperatureHumidityModel.class);
        when(model.predict(32, 66)).thenReturn(new TemperatureHumidityModel.Prediction(0, .92, "test"));
        var hybrid = new HybridRiskService(model, 1800, 2500, 3200);
        double[] inputs = {0, 1799, 1800, 2499, 2500, 3199, 3200, 4095};
        int[] expected = {0, 0, 1, 1, 2, 2, 3, 3};
        for (int i = 0; i < inputs.length; i++) {
            var result = hybrid.predict("ESP", 32d, 66d, inputs[i], null);
            assertEquals(expected[i], result.riskLevel());
            assertEquals(0, result.aiRiskLevel());
            assertEquals(expected[i], result.gasRiskLevel());
            assertEquals(.92, result.confidence());
        }
        verify(model, times(inputs.length)).predict(32, 66);
    }

    @Test void choosesHigherSourceAndReturnsMatchingActuatorCommand() {
        var model = mock(TemperatureHumidityModel.class);
        when(model.predict(32, 66)).thenReturn(new TemperatureHumidityModel.Prediction(1, .81, "test"));
        var hybrid = new HybridRiskService(model, 1800, 2500, 3200);
        var ai = hybrid.predict("ESP", 32d, 66d, 400d, null);
        assertEquals(1, ai.riskLevel()); assertEquals("AI", ai.decisionSource());
        assertEquals("RED_LED", ai.actionCode());
        var gas = hybrid.predict("ESP", 32d, 66d, 3200d, null);
        assertEquals(3, gas.riskLevel()); assertEquals("GAS_THRESHOLD", gas.decisionSource());
        assertEquals("RED_LED_FAN_BUZZER", gas.actionCode());
        assertEquals(.81, gas.confidence());
        assertEquals("BOTH", hybrid.predict("ESP", 32d, 66d, 1800d, null).decisionSource());
    }

    @Test void aiCanRaiseToLevelThreeWhileGasIsLow() {
        var model = mock(TemperatureHumidityModel.class);
        when(model.predict(70, 65)).thenReturn(new TemperatureHumidityModel.Prediction(3, .85, "four-class"));
        var result = new HybridRiskService(model, 1800, 2500, 3200).predict("ESP", 70d, 65d, 400d, null);
        assertEquals(3, result.aiRiskLevel());
        assertEquals(0, result.gasRiskLevel());
        assertEquals(3, result.riskLevel());
        assertEquals("AI", result.decisionSource());
        assertEquals("RED_LED_FAN_BUZZER", result.actionCode());
    }

    @Test void invalidInputsAndMisorderedThresholdsAreRejected() {
        var model = mock(TemperatureHumidityModel.class);
        var hybrid = new HybridRiskService(model, 1800, 2500, 3200);
        for (Double gas : new Double[]{null, Double.NaN, 4096d, -1d, 20.5}) {
            var prediction = hybrid.predict("ESP", 32d, 66d, gas, null);
            assertNull(prediction.riskLevel()); assertNull(prediction.actionCode());
            assertEquals("INVALID_SENSOR_DATA", prediction.predictionStatus());
        }
        verifyNoInteractions(model);
        assertThrows(IllegalArgumentException.class, () -> new HybridRiskService(model, 2500, 1800, 3200));
        assertThrows(IllegalArgumentException.class, () -> new HybridRiskService(model, 1800, 2500, 5000));
    }
}
