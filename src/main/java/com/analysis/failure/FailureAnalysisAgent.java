package com.analysis.failure;

import com.analysis.ai.OpenAiCompatibleConfig;

import java.nio.file.Path;

public class FailureAnalysisAgent {
    private static final String AGENT_BANNER = "=== AI Kafka Validator - Failure Analysis ===";

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        String mode = System.getProperty("failure.analysis.mode", "all").trim().toLowerCase();
        FailureAnalysisService.Result result = new FailureAnalysisService(projectRoot).generate(mode);
        System.out.println(AGENT_BANNER);
        System.out.println("Mode: " + OpenAiCompatibleConfig.forFailureAnalysis().modeDescription());
        System.out.println(result.getMarkdown());
        System.out.println("Failure analysis markdown written to " + result.getMarkdownPath());
        if (result.getHtmlPath() != null) {
            System.out.println("Failure analysis HTML written to " + result.getHtmlPath());
            System.out.println("Mirrored into test reports: "
                    + result.getSurefireHtmlPath() + " and " + result.getCucumberHtmlPath());
        }
    }
}
