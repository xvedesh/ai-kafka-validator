package com.analysis.failure;

import com.analysis.failure.model.FailureAnalysis;
import com.analysis.failure.model.FailureEvidence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class FailureAnalysisService {
    private final Path projectRoot;

    public FailureAnalysisService(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public Result generate(String mode) throws IOException {
        CucumberFailureParser parser = new CucumberFailureParser(projectRoot);
        List<FailureEvidence> failures = parser.parse();
        if ("latest".equalsIgnoreCase(mode) && !failures.isEmpty()) {
            failures = Collections.singletonList(failures.get(failures.size() - 1));
        }

        FailureAnalyzer analyzer = new FailureAnalyzer(projectRoot);
        List<FailureAnalysis> analyses = failures.stream()
                .map(analyzer::analyze)
                .toList();

        String aiSummary = new FailureLlmSummaryGenerator(projectRoot).tryGenerateSummary(analyses, mode);
        String rerunCommand = analyses.isEmpty() ? null
                : new RerunCommandBuilder(projectRoot).build(analyses);
        String markdown = FailureFormatter.format(analyses, mode, aiSummary, rerunCommand);
        String html = FailureHtmlFormatter.format(analyses, mode, aiSummary, rerunCommand);

        Path targetDir = projectRoot.resolve("target");
        Path markdownPath = targetDir.resolve("failure-analysis.md");
        Path htmlPath = targetDir.resolve("failure-analysis.html");
        Path surefireHtmlPath = targetDir.resolve("surefire-reports/failure-analysis.html");
        Path cucumberHtmlPath = targetDir.resolve("cucumber/cucumber-html-reports/failure-analysis.html");

        Files.createDirectories(targetDir);
        if (analyses.isEmpty()) {
            writeString(markdownPath, markdown);
            deleteIfExists(htmlPath);
            deleteIfExists(surefireHtmlPath);
            deleteIfExists(cucumberHtmlPath);
            return new Result(analyses, markdown, null, markdownPath, null, null, null, aiSummary, null);
        }

        writeString(markdownPath, markdown);
        writeString(htmlPath, html);
        writeString(surefireHtmlPath, html);
        writeString(cucumberHtmlPath, html);

        return new Result(analyses, markdown, html, markdownPath, htmlPath, surefireHtmlPath, cucumberHtmlPath, aiSummary, rerunCommand);
    }

    private void writeString(Path path, String value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    private void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    public static class Result {
        private final List<FailureAnalysis> analyses;
        private final String markdown;
        private final String html;
        private final Path markdownPath;
        private final Path htmlPath;
        private final Path surefireHtmlPath;
        private final Path cucumberHtmlPath;
        private final String aiSummary;
        private final String rerunCommand;

        public Result(List<FailureAnalysis> analyses, String markdown, String html, Path markdownPath,
                      Path htmlPath, Path surefireHtmlPath, Path cucumberHtmlPath, String aiSummary,
                      String rerunCommand) {
            this.analyses = analyses;
            this.markdown = markdown;
            this.html = html;
            this.markdownPath = markdownPath;
            this.htmlPath = htmlPath;
            this.surefireHtmlPath = surefireHtmlPath;
            this.cucumberHtmlPath = cucumberHtmlPath;
            this.aiSummary = aiSummary;
            this.rerunCommand = rerunCommand;
        }

        public List<FailureAnalysis> getAnalyses() {
            return analyses;
        }

        public String getMarkdown() {
            return markdown;
        }

        public String getHtml() {
            return html;
        }

        public Path getMarkdownPath() {
            return markdownPath;
        }

        public Path getHtmlPath() {
            return htmlPath;
        }

        public Path getSurefireHtmlPath() {
            return surefireHtmlPath;
        }

        public Path getCucumberHtmlPath() {
            return cucumberHtmlPath;
        }

        public String getAiSummary() {
            return aiSummary;
        }

        public String getRerunCommand() {
            return rerunCommand;
        }
    }
}
