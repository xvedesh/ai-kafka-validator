package com.analysis.ai.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProjectKnowledgeBase {
    private static final int GENERIC_CHUNK_SIZE = 40;
    private static final int GENERIC_CHUNK_OVERLAP = 8;

    private final Path projectRoot;

    public ProjectKnowledgeBase(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public List<ProjectChunk> load() throws IOException {
        List<Path> files = collectKnowledgeFiles();
        List<ProjectChunk> chunks = new ArrayList<>();

        for (Path file : files) {
            chunks.addAll(chunkFile(file));
        }

        return chunks;
    }

    private List<Path> collectKnowledgeFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        addIfExists(files, projectRoot.resolve("README.md"));
        addIfExists(files, projectRoot.resolve("pom.xml"));
        addIfExists(files, projectRoot.resolve("config.properties"));
        addIfExists(files, projectRoot.resolve("docker-compose.yml"));
        addIfExists(files, projectRoot.resolve("docker-compose.kafka.yml"));
        addIfExists(files, projectRoot.resolve("Dockerfile"));
        addIfExists(files, projectRoot.resolve("testng.xml"));
        addIfExists(files, projectRoot.resolve("testng-rerun.xml"));

        addTree(files, projectRoot.resolve("docs"));
        addTree(files, projectRoot.resolve("server"));
        addTree(files, projectRoot.resolve("src/test/resources/features"));
        addTree(files, projectRoot.resolve("src/test/java/com/api"));
        addTree(files, projectRoot.resolve("src/test/java/com/kafka"));
        addTree(files, projectRoot.resolve("src/test/java/com/step_defs"));
        addTree(files, projectRoot.resolve("src/test/java/com/runners"));
        addTree(files, projectRoot.resolve("src/test/java/com/utils"));
        addTree(files, projectRoot.resolve("src/main/java/com/analysis/failure"));
        addTree(files, projectRoot.resolve("src/main/java/com/analysis/ai"));

        return files.stream()
                .filter(this::isSupportedKnowledgeFile)
                .filter(this::isProjectKnowledgeFile)
                .sorted(Comparator.comparing(path -> projectRoot.relativize(path).toString()))
                .collect(Collectors.toList());
    }

    private void addTree(List<Path> files, Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> pathStream = Files.walk(root)) {
            pathStream
                    .filter(Files::isRegularFile)
                    .forEach(files::add);
        }
    }

    private void addIfExists(List<Path> files, Path file) {
        if (Files.exists(file)) {
            files.add(file);
        }
    }

    private boolean isSupportedKnowledgeFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".md")
                || fileName.endsWith(".feature")
                || fileName.endsWith(".java")
                || fileName.endsWith(".js")
                || fileName.endsWith(".json")
                || fileName.endsWith(".xml")
                || fileName.endsWith(".properties")
                || fileName.endsWith(".yml")
                || fileName.endsWith(".yaml");
    }

    private boolean isProjectKnowledgeFile(Path file) {
        String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');
        return !relativePath.startsWith("server/node_modules/")
                && !relativePath.startsWith("target/")
                && !relativePath.startsWith(".git/");
    }

    private List<ProjectChunk> chunkFile(Path file) throws IOException {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');

        if (fileName.endsWith(".md")) {
            return chunkMarkdown(relativePath, lines);
        }
        if (fileName.endsWith(".feature")) {
            return chunkFeature(relativePath, lines);
        }
        return chunkGeneric(relativePath, lines);
    }

    private List<ProjectChunk> chunkMarkdown(String relativePath, List<String> lines) {
        List<ProjectChunk> chunks = new ArrayList<>();
        int start = 0;
        String title = relativePath;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (index > start && line.startsWith("## ")) {
                chunks.add(buildChunk(relativePath, title, start, index - 1, lines));
                start = index;
                title = line.substring(3).trim();
            } else if (index == 0 && line.startsWith("# ")) {
                title = line.substring(2).trim();
            }
        }

        if (!lines.isEmpty()) {
            chunks.add(buildChunk(relativePath, title, start, lines.size() - 1, lines));
        }
        return chunks;
    }

    private List<ProjectChunk> chunkFeature(String relativePath, List<String> lines) {
        List<ProjectChunk> chunks = new ArrayList<>();
        int start = 0;
        String title = relativePath;

        for (int index = 0; index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("Feature:")) {
                title = trimmed;
                start = index;
            }
            if (index > start && (trimmed.startsWith("Scenario:") || trimmed.startsWith("Scenario Outline:"))) {
                chunks.add(buildChunk(relativePath, title, start, index - 1, lines));
                start = Math.max(0, findScenarioTagStart(lines, index));
                title = trimmed;
            }
        }

        if (!lines.isEmpty()) {
            chunks.add(buildChunk(relativePath, title, start, lines.size() - 1, lines));
        }
        return chunks;
    }

    private int findScenarioTagStart(List<String> lines, int scenarioIndex) {
        int cursor = scenarioIndex - 1;
        while (cursor >= 0 && lines.get(cursor).trim().startsWith("@")) {
            cursor--;
        }
        return cursor + 1;
    }

    private List<ProjectChunk> chunkGeneric(String relativePath, List<String> lines) {
        List<ProjectChunk> chunks = new ArrayList<>();
        if (lines.isEmpty()) {
            return chunks;
        }

        for (int start = 0; start < lines.size(); start += GENERIC_CHUNK_SIZE - GENERIC_CHUNK_OVERLAP) {
            int end = Math.min(lines.size() - 1, start + GENERIC_CHUNK_SIZE - 1);
            String title = firstMeaningfulLine(lines, start, end, relativePath);
            chunks.add(buildChunk(relativePath, title, start, end, lines));
            if (end == lines.size() - 1) {
                break;
            }
        }

        return chunks;
    }

    private String firstMeaningfulLine(List<String> lines, int start, int end, String fallback) {
        for (int index = start; index <= end; index++) {
            String trimmed = lines.get(index).trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 90 ? trimmed.substring(0, 90) : trimmed;
            }
        }
        return fallback;
    }

    private ProjectChunk buildChunk(String path, String title, int start, int end, List<String> lines) {
        String content = lines.subList(start, end + 1).stream().collect(Collectors.joining(System.lineSeparator()));
        return new ProjectChunk(path, title, start + 1, end + 1, content);
    }
}
