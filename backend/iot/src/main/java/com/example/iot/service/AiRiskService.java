package com.example.iot.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Service
public class AiRiskService {
    private static final String[] STATUSES = {"NORMAL", "WARNING", "DANGER", "EMERGENCY"};
    private static final String[] ACTIONS = {"GREEN_LED", "RED_LED", "RED_LED_FAN", "RED_LED_FAN_BUZZER"};
    private final Forest tvoc;
    private final Forest mq2;

    public record Prediction(Integer riskLevel, Double confidence, String aiStatus,
            String actionCode, String modelVersion, String predictionStatus) {}

    private record Tree(int[] left, int[] right, int[] feature, double[] threshold, double[][] probabilities) {}
    private record Forest(List<Tree> trees, double[] medians, String version, Set<String> devices, boolean adc) {}

    public AiRiskService() throws IOException {
        this(new ClassPathResource("ai/smoke-risk.rf.gz").getInputStream(), optionalMq2());
    }

    AiRiskService(InputStream tvocInput, InputStream mq2Input) throws IOException {
        tvoc = readForest(tvocInput, false);
        mq2 = mq2Input == null ? null : readForest(mq2Input, true);
    }

    private static InputStream optionalMq2() throws IOException {
        var resource = new ClassPathResource("ai/mq2-risk.rf.gz");
        return resource.exists() ? resource.getInputStream() : null;
    }

    private static Forest readForest(InputStream source, boolean expectedAdc) throws IOException {
        List<Tree> trees = new ArrayList<>();
        double[] medians = new double[3];
        Set<String> devices = new HashSet<>();
        try (var input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                source)))) {
            if (input.readInt() != 0x53575246) throw new IOException("Invalid AI model magic");
            int format = input.readInt();
            if (format != 1 && format != 2) {
                throw new IOException("Unsupported AI model format");
            }
            String version = new String(input.readNBytes(64), StandardCharsets.US_ASCII);
            if (!version.matches("[0-9a-f]{64}")) throw new IOException("Invalid AI model hash");
            boolean adc = false;
            if (format == 2) {
                int kind = input.readInt();
                if (kind != 0 && kind != 1) throw new IOException("Unknown model input unit");
                adc = kind == 1;
                int scopeCount = input.readInt();
                if (scopeCount < 0 || scopeCount > 10000) throw new IOException("Invalid device scope");
                for (int d = 0; d < scopeCount; d++) {
                    int length = input.readInt();
                    if (length < 1 || length > 1024) throw new IOException("Invalid device code length");
                    byte[] bytes = input.readNBytes(length);
                    if (bytes.length != length) throw new EOFException();
                    devices.add(new String(bytes, StandardCharsets.UTF_8));
                }
            }
            if (adc != expectedAdc || (adc && devices.isEmpty())) throw new IOException("AI input unit/scope mismatch");
            for (int i = 0; i < 3; i++) medians[i] = input.readDouble();
            int count = input.readInt();
            if (count < 1 || count > 10000) throw new IOException("Invalid forest size");
            for (int t = 0; t < count; t++) {
                int size = input.readInt();
                Tree tree = new Tree(new int[size], new int[size], new int[size], new double[size], new double[size][4]);
                for (int n = 0; n < size; n++) {
                    tree.left[n] = input.readInt();
                    tree.right[n] = input.readInt();
                    tree.feature[n] = input.readInt();
                    tree.threshold[n] = input.readDouble();
                    for (int c = 0; c < 4; c++) tree.probabilities[n][c] = input.readDouble();
                }
                trees.add(tree);
            }
            if (trees.isEmpty()) throw new IOException("Empty AI forest");
            return new Forest(List.copyOf(trees), medians, version, Set.copyOf(devices), adc);
        }
    }

    public Prediction predict(Double temperature, Double humidity, Double gasTvocPpb) {
        if (temperature == null || humidity == null || !Double.isFinite(temperature) || !Double.isFinite(humidity)) {
            return pending("INVALID_SENSOR_DATA", tvoc.version);
        }
        if (gasTvocPpb == null || !Double.isFinite(gasTvocPpb) || gasTvocPpb < 0) {
            return pending("GAS_CALIBRATION_REQUIRED", tvoc.version);
        }
        return infer(temperature, humidity, gasTvocPpb);
    }

    Prediction infer(double... values) {
        return infer(tvoc, values);
    }

    public Prediction predict(String deviceCode, Double temperature, Double humidity, Double gasAdc, Double gasTvocPpb) {
        if (gasTvocPpb != null) return predict(temperature, humidity, gasTvocPpb);
        if (temperature == null || humidity == null || !Double.isFinite(temperature) || !Double.isFinite(humidity)
                || temperature < -100 || temperature > 200 || humidity < 0 || humidity > 100
                || gasAdc == null || !Double.isFinite(gasAdc) || gasAdc < 0 || gasAdc > 4095 || gasAdc != Math.rint(gasAdc)) {
            return pending("INVALID_SENSOR_DATA", mq2 == null ? null : mq2.version);
        }
        if (mq2 == null) return pending("MQ2_MODEL_REQUIRED", null);
        if (deviceCode == null || !mq2.devices.contains(deviceCode)) return pending("MQ2_DEVICE_NOT_TRAINED", mq2.version);
        return infer(mq2, temperature, humidity, gasAdc);
    }

    private Prediction infer(Forest forest, double... values) {
        double[] features = new double[3];
        for (int i = 0; i < 3; i++) features[i] = (float) (Double.isNaN(values[i]) ? forest.medians[i] : values[i]);
        double[] probabilities = new double[4];
        for (Tree tree : forest.trees) {
            int node = 0;
            while (tree.left[node] != -1) {
                node = features[tree.feature[node]] <= tree.threshold[node] ? tree.left[node] : tree.right[node];
            }
            for (int c = 0; c < 4; c++) probabilities[c] += tree.probabilities[node][c] / forest.trees.size();
        }
        int best = 0;
        for (int c = 1; c < 4; c++) if (probabilities[c] > probabilities[best]) best = c;
        return new Prediction(best, probabilities[best], STATUSES[best], ACTIONS[best], forest.version, "READY");
    }

    private Prediction pending(String status, String version) {
        return new Prediction(null, null, null, null, version, status);
    }
}
