package com.analysis.ai.rag;

public record ProjectChunk(
        String path,
        String title,
        int startLine,
        int endLine,
        String content
) {
    public String location() {
        return startLine > 0 ? path + ":" + startLine : path;
    }

    public String searchableText() {
        return (path + " " + title + " " + content).toLowerCase();
    }
}
