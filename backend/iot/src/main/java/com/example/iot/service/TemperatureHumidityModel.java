package com.example.iot.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Service
public class TemperatureHumidityModel {
    private record Tree(int[] left, int[] right, int[] feature, double[] threshold, double[][] probabilities) {}
    public record Prediction(int level, double confidence, String version) {}
    private final List<Tree> trees = new ArrayList<>();
    private final String version;

    public TemperatureHumidityModel() throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(
                new ClassPathResource("ai/temperature-humidity.rf.gz").getInputStream())))) {
            if (input.readInt() != 0x53575448 || input.readInt() != 2) throw new IOException("Invalid four-class temperature/humidity model");
            version = new String(input.readNBytes(64), StandardCharsets.US_ASCII);
            input.readDouble(); input.readDouble();
            int count = input.readInt();
            if (count < 1 || count > 10000) throw new IOException("Invalid forest size");
            for (int t = 0; t < count; t++) {
                int size = input.readInt();
                Tree tree = new Tree(new int[size], new int[size], new int[size], new double[size], new double[size][4]);
                for (int n = 0; n < size; n++) {
                    tree.left[n] = input.readInt(); tree.right[n] = input.readInt();
                    tree.feature[n] = input.readInt(); tree.threshold[n] = input.readDouble();
                    for (int c = 0; c < 4; c++) tree.probabilities[n][c] = input.readDouble();
                    if (tree.left[n] != -1 && (tree.feature[n] < 0 || tree.feature[n] > 1)) throw new IOException("Unexpected gas feature");
                }
                trees.add(tree);
            }
        }
    }

    public Prediction predict(double temperature, double humidity) {
        if (!Double.isFinite(temperature) || !Double.isFinite(humidity)) throw new IllegalArgumentException("Invalid sensor value");
        double[] features = {(float) temperature, (float) humidity};
        double[] probability = new double[4];
        for (Tree tree : trees) {
            int node = 0;
            while (tree.left[node] != -1) node = features[tree.feature[node]] <= tree.threshold[node] ? tree.left[node] : tree.right[node];
            for (int c = 0; c < 4; c++) probability[c] += tree.probabilities[node][c] / trees.size();
        }
        int level = 0;
        for (int c = 1; c < 4; c++) if (probability[c] > probability[level]) level = c;
        return new Prediction(level, Math.max(0, Math.min(1, probability[level])), version);
    }
}
