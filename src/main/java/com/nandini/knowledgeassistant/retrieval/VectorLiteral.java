package com.nandini.knowledgeassistant.retrieval;

/**
 * Formats a float[] as a pgvector text literal ({@code [0.1,0.2,...]}). Passing the vector as
 * a bound parameter cast to {@code vector} avoids a driver-specific type while remaining
 * safe from SQL injection.
 */
final class VectorLiteral {

    private VectorLiteral() {
    }

    static String of(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            float value = vector[i];
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException("Embedding contains a non-finite value at index " + i);
            }
            builder.append(value);
        }
        return builder.append(']').toString();
    }
}
