package com.analysis.ai.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RetrievalService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9@._-]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "this", "that", "from", "into", "what", "where", "when",
            "does", "how", "why", "can", "could", "would", "should", "about", "have", "has",
            "only", "your", "their", "there", "which", "just", "after", "before", "need", "help",
            "start", "run", "use", "using", "inside", "work", "works", "framework"
    );

    private final List<ProjectChunk> chunks;

    public RetrievalService(List<ProjectChunk> chunks) {
        this.chunks = chunks;
    }

    public List<ProjectChunk> retrieve(String question, int limit) {
        Set<String> queryTokens = tokenize(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);

        for (ProjectChunk chunk : chunks) {
            String searchableText = chunk.searchableText();
            int score = 0;
            for (String token : queryTokens) {
                if (searchableText.contains(token)) {
                    score += 3;
                    if (chunk.path().toLowerCase(Locale.ROOT).contains(token)) {
                        score += 2;
                    }
                    if (chunk.title().toLowerCase(Locale.ROOT).contains(token)) {
                        score += 2;
                    }
                }
            }
            if (normalizedQuestion.contains("kafka") && searchableText.contains("kafka")) {
                score += 3;
            }
            if (normalizedQuestion.contains("docker") && searchableText.contains("docker")) {
                score += 3;
            }
            if (normalizedQuestion.contains("failure") && searchableText.contains("failure")) {
                score += 3;
            }
            if (normalizedQuestion.contains("negative") && searchableText.contains("negative")) {
                score += 3;
            }
            if (normalizedQuestion.contains("account") && searchableText.contains("account")) {
                score += 2;
            }
            if (normalizedQuestion.contains("portfolio") && searchableText.contains("portfolio")) {
                score += 2;
            }
            if (normalizedQuestion.contains("transaction") && searchableText.contains("transaction")) {
                score += 2;
            }
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed()
                        .thenComparing(scoredChunk -> scoredChunk.chunk().path()))
                .map(ScoredChunk::chunk)
                .limit(limit)
                .toList();
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private record ScoredChunk(ProjectChunk chunk, int score) {
    }
}
