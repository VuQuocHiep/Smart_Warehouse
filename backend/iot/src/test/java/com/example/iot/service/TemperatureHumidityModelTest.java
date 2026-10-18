package com.example.iot.service;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class TemperatureHumidityModelTest {
    @Test void matchesPythonTwoFeatureModel() throws Exception {
        var model = new TemperatureHumidityModel();
        var classes = new HashSet<Integer>();
        int count = 0;
        try (var reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
                "/ai/temperature-humidity-parity.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                var values = line.split(",");
                var result = model.predict(Double.parseDouble(values[0]), Double.parseDouble(values[1]));
                assertEquals(Integer.parseInt(values[2]), result.level(), line);
                assertEquals(Double.parseDouble(values[3]), result.confidence(), 1e-12, line);
                assertTrue(result.confidence() >= 0 && result.confidence() <= 1);
                classes.add(result.level()); count++;
            }
        }
        assertEquals(java.util.Set.of(0, 1, 2, 3), classes);
        assertTrue(count >= 160);
    }
}
