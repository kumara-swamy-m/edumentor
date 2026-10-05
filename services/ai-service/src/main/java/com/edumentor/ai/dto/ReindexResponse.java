package com.edumentor.ai.dto;

public record ReindexResponse(String model, int indexed, int failed, int removed) {
}