package com.cotizaia.agent.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The offline embedder must be deterministic, normalized and roughly topical, so tests and CI can run retrieval. */
class StubEmbeddingClientTests {

    private final StubEmbeddingClient client = new StubEmbeddingClient();

    @Test
    void isDeterministicNormalizedAnd256Dims() {
        float[] first = client.embed(List.of("Sitio web de 8 páginas"), EmbeddingPurpose.DOCUMENT).get(0);
        float[] again = client.embed(List.of("Sitio web de 8 páginas"), EmbeddingPurpose.QUERY).get(0);
        assertThat(first).hasSize(256).containsExactly(again);
        assertThat(norm(first)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(client.provider()).isEqualTo("stub");
    }

    @Test
    void ignoresAccentsAndCase() {
        float[] accented = client.embed(List.of("Diseño PÁGINAS"), EmbeddingPurpose.DOCUMENT).get(0);
        float[] plain = client.embed(List.of("diseno paginas"), EmbeddingPurpose.DOCUMENT).get(0);
        assertThat(accented).containsExactly(plain);
    }

    @Test
    void relatedTextsAreCloserThanUnrelatedOnes() {
        List<float[]> vectors = client.embed(List.of(
                "sitio informativo de 8 paginas con diseño a medida",
                "pagina web informativa de 8 paginas a medida",
                "tienda en linea con 300 productos y pasarela de pagos"), EmbeddingPurpose.DOCUMENT);
        assertThat(cosine(vectors.get(0), vectors.get(1))).isGreaterThan(cosine(vectors.get(0), vectors.get(2)));
    }

    @Test
    void emptyTextGivesZeroVectorAndOrderIsPreserved() {
        List<float[]> vectors = client.embed(List.of("tienda", "", "tienda"), EmbeddingPurpose.DOCUMENT);
        assertThat(vectors).hasSize(3);
        assertThat(norm(vectors.get(1))).isZero();
        assertThat(vectors.get(0)).containsExactly(vectors.get(2));
    }

    static double norm(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    static double cosine(float[] left, float[] right) {
        double dot = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return dot / (norm(left) * norm(right));
    }
}
