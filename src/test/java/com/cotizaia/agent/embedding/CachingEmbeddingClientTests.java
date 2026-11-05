package com.cotizaia.agent.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proxy: identical texts are embedded once per purpose; only misses reach the real provider, in order. */
class CachingEmbeddingClientTests {

    private final RecordingClient delegate = new RecordingClient();

    private final CachingEmbeddingClient proxy = new CachingEmbeddingClient(delegate, 100);

    @Test
    void secondIdenticalCallNeverReachesTheDelegate() {
        List<float[]> first = proxy.embed(List.of("tienda con 300 productos"), EmbeddingPurpose.DOCUMENT);
        List<float[]> second = proxy.embed(List.of("tienda con 300 productos"), EmbeddingPurpose.DOCUMENT);
        assertThat(delegate.calls).containsExactly(List.of("tienda con 300 productos"));
        assertThat(second.get(0)).containsExactly(first.get(0));
    }

    @Test
    void forwardsOnlyMissesAndKeepsTheCallerOrder() {
        proxy.embed(List.of("b"), EmbeddingPurpose.DOCUMENT);
        List<float[]> result = proxy.embed(List.of("a", "b", "c"), EmbeddingPurpose.DOCUMENT);
        assertThat(delegate.calls).containsExactly(List.of("b"), List.of("a", "c"));
        assertThat(result).extracting(vector -> vector[0]).containsExactly(1f, 1f, 1f);
        assertThat(result).extracting(vector -> vector[1]).containsExactly((float) "a".hashCode(),
                (float) "b".hashCode(), (float) "c".hashCode());
    }

    @Test
    void purposeIsPartOfTheKey() {
        proxy.embed(List.of("x"), EmbeddingPurpose.DOCUMENT);
        proxy.embed(List.of("x"), EmbeddingPurpose.QUERY);
        assertThat(delegate.calls).hasSize(2);
    }

    @Test
    void evictsLeastRecentlyUsedBeyondCapacity() {
        CachingEmbeddingClient small = new CachingEmbeddingClient(delegate, 2);
        small.embed(List.of("a"), EmbeddingPurpose.DOCUMENT);
        small.embed(List.of("b"), EmbeddingPurpose.DOCUMENT);
        small.embed(List.of("a"), EmbeddingPurpose.DOCUMENT);
        small.embed(List.of("c"), EmbeddingPurpose.DOCUMENT);
        small.embed(List.of("b"), EmbeddingPurpose.DOCUMENT);
        assertThat(delegate.calls).containsExactly(List.of("a"), List.of("b"), List.of("c"), List.of("b"));
    }

    @Test
    void allHitsMakeNoDelegateCallAndProviderIsTheDelegates() {
        proxy.embed(List.of("a"), EmbeddingPurpose.DOCUMENT);
        proxy.embed(List.of("a", "a"), EmbeddingPurpose.DOCUMENT);
        assertThat(delegate.calls).hasSize(1);
        assertThat(proxy.provider()).isEqualTo("recording");
    }

    /** Vector = [1, text.hashCode()] so results can be traced back to their text. */
    static class RecordingClient implements EmbeddingClient {

        final List<List<String>> calls = new ArrayList<>();

        @Override
        public List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
            calls.add(List.copyOf(texts));
            return texts.stream().map(text -> new float[] {1f, text.hashCode()}).toList();
        }

        @Override
        public String provider() {
            return "recording";
        }
    }
}
