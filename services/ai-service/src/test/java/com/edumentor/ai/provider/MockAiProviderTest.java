package com.edumentor.ai.provider;

import com.edumentor.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockAiProviderTest {

    private final MockAiProvider provider = new MockAiProvider(new AiProperties("mock", 512, "memory", 10));

    @Test
    void sameTextAlwaysGivesTheSameVector() {
        assertThat(provider.embed("Exam: KCET. Branch: CSE")).containsExactly(provider.embed("Exam: KCET. Branch: CSE"));
    }

    @Test
    void vectorsHaveTheConfiguredSizeAndUnitLength() {
        float[] vector = provider.embed("Exam: KCET. College: RVCE. Branch: CSE");

        assertThat(vector).hasSize(512);
        double norm = Math.sqrt(Arrays.stream(toDoubles(vector)).map(v -> v * v).sum());
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void relatedProfilesAreMoreSimilarThanUnrelatedOnes() {
        float[] query = provider.embed("Exam: KCET. Preferred colleges: RVCE. Preferred branches: CSE");
        float[] related = provider.embed("Exam: KCET. College: RVCE. Branch: CSE. Location: Bengaluru");
        float[] unrelated = provider.embed("Exam: NEET. College: AIIMS. Branch: MBBS. Location: Delhi");

        assertThat(dot(query, related)).isGreaterThan(dot(query, unrelated) + 0.3);
    }

    @Test
    void aliasesMeetInTheMiddle() {
        assertThat(dot(provider.embed("Computer"), provider.embed("CSE"))).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void textWithoutContentIsRejected() {
        assertThatThrownBy(() -> provider.embed("   ...  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.embed("Exam: Rank: 123")).isInstanceOf(IllegalArgumentException.class);
    }

    private double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    private double[] toDoubles(float[] v) {
        double[] d = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            d[i] = v[i];
        }
        return d;
    }
}