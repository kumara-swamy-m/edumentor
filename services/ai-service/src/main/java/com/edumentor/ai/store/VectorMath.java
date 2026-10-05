package com.edumentor.ai.store;

public final class VectorMath {

    private VectorMath() {
    }

    /** pgvector text format: [0.1,0.2,...]. Rejects NaN and infinity. */
    public static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) {
                throw new IllegalArgumentException("Vector contains a non-finite value");
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}