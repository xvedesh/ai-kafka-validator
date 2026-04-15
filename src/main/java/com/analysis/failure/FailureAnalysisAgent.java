package com.analysis.failure;

import com.analysis.ai.OpenAiCompatibleConfig;
import com.analysis.failure.model.FailureAnalysis;
import com.analysis.failure.model.FixProposal;

import java.nio.file.Path;
import java.util.List;

public class FailureAnalysisAgent {
    private static final String AGENT_BANNER = "=== AI Kafka Validator - Failure Analysis ===";
    private static final String DIVIDER = "---------------------------------------------";

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        String mode = System.getProperty("failure.analysis.mode", "all").trim().toLowerCase();
        FailureAnalysisService.Result result = new FailureAnalysisService(projectRoot).generate(mode);

        System.out.println(AGENT_BANNER);
        System.out.println("Mode: " + OpenAiCompatibleConfig.forFailureAnalysis().modeDescription());
        System.out.println("Failure analysis markdown written to " + result.getMarkdownPath());
        if (result.getHtmlPath() != null) {
            System.out.println("Failure analysis HTML written to " + result.getHtmlPath());
            System.out.println("Mirrored into test reports: "
                    + result.getSurefireHtmlPath() + " and " + result.getCucumberHtmlPath());
        }

        List<FailureAnalysis> analyses = result.getAnalyses();
        if (analyses.isEmpty()) {
            return;
        }

        System.out.println(DIVIDER);
        System.out.println("Proposed Fixes");
        System.out.println(DIVIDER);
        for (int i = 0; i < analyses.size(); i++) {
            FailureAnalysis analysis = analyses.get(i);
            System.out.printf("%d. %s [%s]%n",
                    i + 1, analysis.getEvidence().getScenarioName(), analysis.getCategory());
            List<FixProposal> fixes = analysis.getFixProposals();
            if (fixes.isEmpty()) {
                System.out.println("   No automated fix available — see Fix Steps in the report.");
            } else {
                for (FixProposal fix : fixes) {
                    System.out.println("   Fix: " + fix.getDescription());
                    if (fix.hasFileEdit()) {
                        String location = fix.getFilePath()
                                + (fix.getLine() != null ? ":" + fix.getLine() : "");
                        System.out.println("   Edit: " + location);
                        System.out.println("     current:  " + fix.getCurrentText());
                        System.out.println("     proposed: " + fix.getProposedText());
                    }
                }
            }
        }

        if (result.getRerunCommand() != null) {
            System.out.println(DIVIDER);
            System.out.println("Rerun Command (after applying fix)");
            System.out.println(DIVIDER);
            System.out.println(result.getRerunCommand());
        }
    }
}
