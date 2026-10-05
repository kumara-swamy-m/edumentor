package com.edumentor.ai.store;

import com.edumentor.ai.domain.MentorDocument;
import com.edumentor.ai.domain.ScoredMentor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.ai.vector-store", havingValue = "memory")
public class InMemoryVectorStore implements VectorStore {

    private record Entry(MentorDocument document, String model, float[] embedding) {
    }

    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void upsert(MentorDocument document, String model, float[] embedding) {
        entries.put(document.mentorId(), new Entry(document, model, embedding.clone()));
    }

    @Override
    public List<ScoredMentor> search(float[] query, String model, String examPath, int limit) {
        return entries.values().stream()
                .filter(e -> e.model().equals(model))
                .filter(e -> examPath == null || examPath.equals(e.document().examPath()))
                .map(e -> new ScoredMentor(e.document(),
                        VectorMath.clamp01(VectorMath.cosine(query, e.embedding()))))
                .sorted(Comparator.comparingDouble(ScoredMentor::score).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public void delete(Long mentorId) {
        entries.remove(mentorId);
    }

    @Override
    public Set<Long> allIds() {
        return new HashSet<>(entries.keySet());
    }

    @Override
    public long count(String model) {
        return entries.values().stream().filter(e -> e.model().equals(model)).count();
    }
}