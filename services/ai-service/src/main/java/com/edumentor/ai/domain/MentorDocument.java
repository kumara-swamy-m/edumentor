package com.edumentor.ai.domain;

import com.edumentor.ai.client.PublicMentor;

/** What is stored for a mentor: the text that was embedded plus the public profile it came from. */
public record MentorDocument(Long mentorId, String examPath, String content, PublicMentor profile) {
}