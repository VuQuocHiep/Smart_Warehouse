package com.example.iot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HybridRiskService {
    private final TemperatureHumidityModel model;
    private final int level1, level2, level3;
    private static final String[] STATUSES = {"NORMAL", "WARNING", "DANGER", "EMERGENCY"};
    private static final String[] ACTIONS = {"GREEN_LED", "RED_LED", "RED_LED_FAN", "RED_LED_FAN_BUZZER"};

    public record Prediction(Integer riskLevel, Double confidence, String aiStatus, String actionCode,
            String modelVersion, String predictionStatus, Integer aiRiskLevel, Integer gasRiskLevel, String decisionSource) {
        public Prediction(Integer riskLevel, Double confidence, String aiStatus, String actionCode, String modelVersion, String predictionStatus) {
            this(riskLevel, confidence, aiStatus, actionCode, modelVersion, predictionStatus, riskLevel, riskLevel == null ? null : 0, riskLevel == null ? null : "AI");
        }
    }

    public HybridRiskService(TemperatureHumidityModel model, @Value("${iot.gas.level1:1800}") int level1,
            @Value("${iot.gas.level2:2500}") int level2, @Value("${iot.gas.level3:3200}") int level3) {
        if (level1 < 1 || level1 >= level2 || level2 >= level3 || level3 > 4095) throw new IllegalArgumentException("Gas thresholds must satisfy 0 < L1 < L2 < L3 <= 4095");
        this.model = model; this.level1 = level1; this.level2 = level2; this.level3 = level3;
    }

    public Prediction predict(String deviceCode, Double temperature, Double humidity, Double gasAdc, Double unusedTvoc) {
        if (temperature == null || humidity == null || gasAdc == null || !Double.isFinite(temperature)
                || !Double.isFinite(humidity) || !Double.isFinite(gasAdc) || temperature < -100 || temperature > 200
                || humidity < 0 || humidity > 100 || gasAdc < 0 || gasAdc > 4095 || gasAdc != Math.rint(gasAdc)) {
            return new Prediction(null, null, null, null, null, "INVALID_SENSOR_DATA");
        }
        var ai = model.predict(temperature, humidity);
        int gasLevel = gasAdc >= level3 ? 3 : gasAdc >= level2 ? 2 : gasAdc >= level1 ? 1 : 0;
        int finalLevel = Math.max(ai.level(), gasLevel);
        String source = ai.level() == gasLevel ? "BOTH" : ai.level() > gasLevel ? "AI" : "GAS_THRESHOLD";
        return new Prediction(finalLevel, ai.confidence(), STATUSES[finalLevel], ACTIONS[finalLevel], ai.version(), "READY", ai.level(), gasLevel, source);
    }
}
