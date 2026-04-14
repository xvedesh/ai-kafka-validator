package com.analysis.failure;

import com.analysis.failure.model.CodeReference;
import com.analysis.failure.model.FailureAnalysis;
import com.analysis.failure.model.FixProposal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class FailureFormatter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FailureFormatter() {
    }

    public static String format(List<FailureAnalysis> analyses, String mode, String aiSummary, String rerunCommand) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# AI Kafka Validator - Failure Analysis Report").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("- Generated at: ").append(LocalDateTime.now().format(FORMATTER)).append(System.lineSeparator());
        markdown.append("- Analysis mode: ").append(mode).append(System.lineSeparator());
        markdown.append("- Failures analyzed: ").append(analyses.size()).append(System.lineSeparator()).append(System.lineSeparator());

        if (aiSummary != null && !aiSummary.isBlank()) {
            markdown.append("## AI Summary").append(System.lineSeparator()).append(System.lineSeparator());
            markdown.append(aiSummary.trim()).append(System.lineSeparator()).append(System.lineSeparator());
        }

        if (analyses.isEmpty()) {
            markdown.append("No failed scenarios were found in `target/cucumber.json`.").append(System.lineSeparator());
            return markdown.toString();
        }

        for (int index = 0; index < analyses.size(); index++) {
            FailureAnalysis analysis = analyses.get(index);
            markdown.append("## ").append(index + 1).append(". ")
                    .append(analysis.getEvidence().getScenarioName()).append(System.lineSeparator()).append(System.lineSeparator());

            markdown.append("### 1. Failure Summary").append(System.lineSeparator());
            markdown.append("- Scenario name: `").append(analysis.getEvidence().getScenarioName()).append("`").append(System.lineSeparator());
            markdown.append("- Failed step: `").append(analysis.getEvidence().getFullFailedStep().trim()).append("`").append(System.lineSeparator());
            markdown.append("- Failure category: `").append(analysis.getCategory()).append("`").append(System.lineSeparator());
            markdown.append("- Likely layer: `").append(analysis.getLikelyLayer()).append("`").append(System.lineSeparator());
            markdown.append("- Short explanation: ").append(analysis.getShortExplanation()).append(System.lineSeparator()).append(System.lineSeparator());

            markdown.append("### 2. What Happened").append(System.lineSeparator());
            markdown.append(analysis.getWhatHappened()).append(System.lineSeparator()).append(System.lineSeparator());

            markdown.append("### 3. Why It Happened (framework-aware)").append(System.lineSeparator());
            markdown.append(analysis.getRootCause()).append(System.lineSeparator()).append(System.lineSeparator());

            markdown.append("### 4. Evidence").append(System.lineSeparator());
            for (String evidencePoint : analysis.getEvidencePoints()) {
                markdown.append("- ").append(evidencePoint).append(System.lineSeparator());
            }
            markdown.append(System.lineSeparator());

            markdown.append("### 5. Relevant Files").append(System.lineSeparator());
            for (CodeReference reference : analysis.getCodeReferences()) {
                markdown.append("- `").append(reference.getLabel()).append("`: `")
                        .append(reference.getPath());
                if (reference.getLine() != null) {
                    markdown.append(":").append(reference.getLine());
                }
                markdown.append("`");
                if (reference.getDetail() != null && !reference.getDetail().isBlank()) {
                    markdown.append(" - ").append(reference.getDetail());
                }
                markdown.append(System.lineSeparator());
            }
            markdown.append(System.lineSeparator());

            markdown.append("### 6. Fix Steps").append(System.lineSeparator());
            for (String nextCheck : analysis.getNextChecks()) {
                markdown.append("- ").append(nextCheck).append(System.lineSeparator());
            }
            markdown.append(System.lineSeparator());

            markdown.append("### 7. Confidence").append(System.lineSeparator());
            markdown.append("- ").append(analysis.getConfidence()).append(System.lineSeparator()).append(System.lineSeparator());

            List<FixProposal> fixProposals = analysis.getFixProposals();
            markdown.append("### 8. Proposed Fix").append(System.lineSeparator());
            if (fixProposals.isEmpty()) {
                markdown.append("No automated fix proposal is available for this failure category. "
                        + "Follow the Fix Steps above.").append(System.lineSeparator());
            } else {
                for (FixProposal fix : fixProposals) {
                    markdown.append("**").append(fix.getDescription()).append("**")
                            .append(System.lineSeparator());
                    if (fix.hasFileEdit()) {
                        markdown.append("- File: `").append(fix.getFilePath()).append("`");
                        if (fix.getLine() != null) {
                            markdown.append(", line ").append(fix.getLine());
                        }
                        markdown.append(System.lineSeparator());
                        markdown.append("- Current:  `").append(fix.getCurrentText()).append("`")
                                .append(System.lineSeparator());
                        markdown.append("- Proposed: `").append(fix.getProposedText()).append("`")
                                .append(System.lineSeparator());
                    }
                }
            }
            markdown.append(System.lineSeparator());
        }

        if (rerunCommand != null && !rerunCommand.isBlank()) {
            markdown.append("---").append(System.lineSeparator()).append(System.lineSeparator());
            markdown.append("## Rerun Command").append(System.lineSeparator());
            markdown.append("After applying the proposed fix, verify it with:").append(System.lineSeparator()).append(System.lineSeparator());
            markdown.append("```bash").append(System.lineSeparator());
            markdown.append(rerunCommand).append(System.lineSeparator());
            markdown.append("```").append(System.lineSeparator());
        }

        return markdown.toString();
    }
}
