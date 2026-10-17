package com.example.iot.service;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class AiRiskServiceTest {
    private AiRiskService withMq2() throws IOException {
        return new AiRiskService(getClass().getResourceAsStream("/ai/smoke-risk.rf.gz"),
                getClass().getResourceAsStream("/ai/mq2-test.rf.gz"));
    }

    @Test void mq2ModelConsumesRawAdcAndMatchesPythonForNonconsecutiveClasses() throws Exception {
        var ai = withMq2();
        var classes = new HashSet<Integer>();
        try (var reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/ai/mq2-test-parity.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                var values = line.split(",");
                var result = ai.predict("TEST_ONLY_MQ2", Double.valueOf(values[0]), Double.valueOf(values[1]), Double.valueOf(values[2]), null);
                assertEquals(Integer.parseInt(values[3]), result.riskLevel());
                assertEquals(Double.parseDouble(values[4]), result.confidence(), 1e-12);
                assertEquals("READY", result.predictionStatus());
                classes.add(result.riskLevel());
            }
        }
        assertEquals(java.util.Set.of(0, 2), classes);
    }

    @Test void mq2CannotUseTvocModelOrUntrainedDevice() throws Exception {
        var missing = new AiRiskService(getClass().getResourceAsStream("/ai/smoke-risk.rf.gz"), null);
        assertEquals("MQ2_MODEL_REQUIRED", missing.predict("ESP1", 31.6, 69.1, 339d, null).predictionStatus());
        var ai = withMq2();
        assertEquals("MQ2_DEVICE_NOT_TRAINED", ai.predict("unknown", 26d, 55d, 339d, null).predictionStatus());
        assertEquals("INVALID_SENSOR_DATA", ai.predict("TEST_ONLY_MQ2", 26d, 55d, 5000d, null).predictionStatus());
        assertEquals("INVALID_SENSOR_DATA", ai.predict("TEST_ONLY_MQ2", 26d, 55d, 339.1, null).predictionStatus());
        assertNull(missing.predict("ESP1", 31.6, 69.1, 339d, null).riskLevel());
    }

    @Test void refusesTvocForestInMq2Slot() {
        assertThrows(IOException.class, () -> new AiRiskService(getClass().getResourceAsStream("/ai/smoke-risk.rf.gz"),
                getClass().getResourceAsStream("/ai/smoke-risk.rf.gz")));
    }

    @Test void matchesOriginalPythonPipelineAcrossAllClasses() throws Exception {
        var ai = new AiRiskService();
        var classes = new HashSet<Integer>();
        try (var reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/ai/parity.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                var values = line.split(",");
                double gas = "nan".equals(values[2]) ? Double.NaN : Double.parseDouble(values[2]);
                var result = ai.infer(Double.parseDouble(values[0]), Double.parseDouble(values[1]), gas);
                assertEquals(Integer.parseInt(values[3]), result.riskLevel(), line);
                assertEquals(Double.parseDouble(values[4]), result.confidence(), 1e-12, line);
                classes.add(result.riskLevel());
            }
        }
        assertEquals(4, classes.size());
    }

    @Test void refusesUncalibratedAndNonFiniteGasInsteadOfUsingAdcOrFixedThresholds() throws Exception {
        var ai = new AiRiskService();
        for (Double gas : new Double[]{null, Double.NaN, Double.POSITIVE_INFINITY, -1d}) {
            var result = ai.predict(31.6, 69.1, gas);
            assertNull(result.riskLevel());
            assertNull(result.actionCode());
            assertEquals("GAS_CALIBRATION_REQUIRED", result.predictionStatus());
        }
        assertEquals("INVALID_SENSOR_DATA", ai.predict(Double.NaN, 50d, 200d).predictionStatus());
    }
}
