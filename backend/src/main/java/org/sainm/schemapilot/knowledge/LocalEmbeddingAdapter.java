package org.sainm.schemapilot.knowledge;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LocalEmbeddingAdapter {
    private static final int DIMENSIONS = 16;

    public float[] embed(String text) {
        var vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        for (var token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")) {
            if (token.length() < 2) {
                continue;
            }
            var index = Math.floorMod(token.hashCode(), DIMENSIONS);
            vector[index] += 1.0f;
        }
        normalize(vector);
        return vector;
    }

    public double cosine(float[] left, float[] right) {
        var dot = 0.0;
        var leftNorm = 0.0;
        var rightNorm = 0.0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private void normalize(float[] vector) {
        var norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            return;
        }
        var scale = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / scale);
        }
    }
}
