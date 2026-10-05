package com.edumentor.ai.service;

import com.edumentor.ai.client.PublicMentor;
import com.edumentor.ai.domain.MentorDocument;
import com.edumentor.ai.dto.IndexStatusResponse;
import com.edumentor.ai.dto.IndexedMentorResponse;
import com.edumentor.ai.dto.ReindexResponse;
import com.edumentor.ai.config.AiProperties;
import com.edumentor.ai.exception.ApiException;
import com.edumentor.ai.provider.AiRecommendationProvider;
import com.edumentor.ai.store.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {

    private final MentorDirectory directory;
    private final EmbeddingService embeddingService;
    private final AiRecommendationProvider provider;
    private final VectorStore store;
    private final AiProperties properties;

    public IndexedMentorResponse indexMentor(Long mentorId, String authorization) {
        Optional<PublicMentor> mentor = directory.find(mentorId, authorization);
        if (mentor.isEmpty()) {
            store.delete(mentorId); // no longer approved (or never existed): make sure it is not recommended
            throw ApiException.mentorNotFound();
        }
        upsert(mentor.get());
        log.info("Mentor embedding stored: mentorId={}, model={}", mentorId, provider.modelId());
        return new IndexedMentorResponse(mentorId, "INDEXED", provider.modelId());
    }

    /** Embeds every approved mentor and removes index entries of mentors that are no longer approved. */
    public ReindexResponse reindexAll(String authorization) {
        List<PublicMentor> approved = directory.listApproved(authorization);
        Set<Long> approvedIds = new HashSet<>();
        int indexed = 0;
        int failed = 0;
        for (PublicMentor mentor : approved) {
            approvedIds.add(mentor.id());
            try {
                upsert(mentor);
                indexed++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Could not index mentorId={}: {}", mentor.id(), ex.getClass().getSimpleName());
            }
        }
        int removed = 0;
        for (Long id : store.allIds()) {
            if (!approvedIds.contains(id)) {
                store.delete(id);
                removed++;
            }
        }
        log.info("Reindex finished: indexed={}, failed={}, removed={}, model={}", indexed, failed, removed,
                provider.modelId());
        return new ReindexResponse(provider.modelId(), indexed, failed, removed);
    }

    public IndexStatusResponse status() {
        return new IndexStatusResponse(provider.modelId(), properties.dimensions(),
                store.count(provider.modelId()));
    }

    private void upsert(PublicMentor mentor) {
        MentorDocument document = DocumentBuilder.document(mentor);
        store.upsert(document, provider.modelId(), embeddingService.embed(document.content()));
    }
}