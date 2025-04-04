package ai;

import java.util.List;

public class CosineSimilarity {
    public static double calculate(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) return 0;

        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += Math.pow(a.get(i), 2);
            normB += Math.pow(b.get(i), 2);
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-8);
    }
}
