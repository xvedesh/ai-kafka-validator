package com.analysis.failure;

import com.analysis.failure.model.CodeReference;
import com.analysis.failure.model.FailureAnalysis;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class FailureHtmlFormatter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FailureHtmlFormatter() {
    }

    public static String format(List<FailureAnalysis> analyses, String mode, String aiSummary) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
                .append("<title>AI Kafka Validator - Failure Analysis Report</title>")
                .append("<style>")
                .append("body{font-family:Arial,sans-serif;margin:32px;line-height:1.5;color:#222;}")
                .append("h1,h2,h3{color:#0f2747;}")
                .append(".meta{background:#f4f7fb;padding:16px;border-radius:8px;margin-bottom:24px;}")
                .append(".card{border:1px solid #d9e2ef;border-radius:10px;padding:20px;margin-bottom:24px;}")
                .append(".summary{background:#eef6ff;border-left:4px solid #0f62fe;padding:16px;border-radius:8px;margin-bottom:24px;}")
                .append("code,pre{background:#f6f8fa;border-radius:6px;}")
                .append("code{padding:2px 6px;}")
                .append("pre{padding:12px;overflow:auto;white-space:pre-wrap;}")
                .append("ul{padding-left:20px;}")
                .append("</style></head><body>");

        html.append("<h1>AI Kafka Validator - Failure Analysis Report</h1>");
        html.append("<div class=\"meta\">")
                .append("<div><strong>Generated at:</strong> ").append(escape(LocalDateTime.now().format(FORMATTER))).append("</div>")
                .append("<div><strong>Analysis mode:</strong> ").append(escape(mode)).append("</div>")
                .append("<div><strong>Failures analyzed:</strong> ").append(analyses.size()).append("</div>")
                .append("</div>");

        if (aiSummary != null && !aiSummary.isBlank()) {
            html.append("<div class=\"summary\"><h2>AI Summary</h2><p>")
                    .append(escape(aiSummary).replace(System.lineSeparator(), "<br>"))
                    .append("</p></div>");
        }

        if (analyses.isEmpty()) {
            html.append("<p>No failed scenarios were found in <code>target/cucumber.json</code>.</p>");
            html.append("</body></html>");
            return html.toString();
        }

        for (int index = 0; index < analyses.size(); index++) {
            FailureAnalysis analysis = analyses.get(index);
            html.append("<div class=\"card\">");
            html.append("<h2>").append(index + 1).append(". ").append(escape(analysis.getEvidence().getScenarioName())).append("</h2>");

            html.append("<h3>1. Failure Summary</h3><ul>")
                    .append("<li><strong>Scenario name:</strong> <code>").append(escape(analysis.getEvidence().getScenarioName())).append("</code></li>")
                    .append("<li><strong>Failed step:</strong> <code>").append(escape(analysis.getEvidence().getFullFailedStep().trim())).append("</code></li>")
                    .append("<li><strong>Failure category:</strong> <code>").append(analysis.getCategory()).append("</code></li>")
                    .append("<li><strong>Likely layer:</strong> <code>").append(escape(analysis.getLikelyLayer())).append("</code></li>")
                    .append("<li><strong>Short explanation:</strong> ").append(escape(analysis.getShortExplanation())).append("</li>")
                    .append("</ul>");

            html.append("<h3>2. What Happened</h3><p>").append(escape(analysis.getWhatHappened())).append("</p>");
            html.append("<h3>3. Why It Happened (framework-aware)</h3><p>").append(escape(analysis.getRootCause())).append("</p>");

            html.append("<h3>4. Evidence</h3><ul>");
            for (String evidencePoint : analysis.getEvidencePoints()) {
                if (evidencePoint.contains("```text")) {
                    html.append("<li><pre>").append(escape(evidencePoint
                            .replace("- ", "")
                            .replace("```text", "")
                            .replace("```", "")
                            .trim())).append("</pre></li>");
                } else {
                    html.append("<li>").append(escape(evidencePoint)).append("</li>");
                }
            }
            html.append("</ul>");

            html.append("<h3>5. Relevant Files</h3><ul>");
            for (CodeReference reference : analysis.getCodeReferences()) {
                html.append("<li><strong>").append(escape(reference.getLabel())).append(":</strong> <code>")
                        .append(escape(reference.getPath()));
                if (reference.getLine() != null) {
                    html.append(":").append(reference.getLine());
                }
                html.append("</code>");
                if (reference.getDetail() != null && !reference.getDetail().isBlank()) {
                    html.append(" - ").append(escape(reference.getDetail()));
                }
                html.append("</li>");
            }
            html.append("</ul>");

            html.append("<h3>6. Fix Steps</h3><ul>");
            for (String nextCheck : analysis.getNextChecks()) {
                html.append("<li>").append(escape(nextCheck)).append("</li>");
            }
            html.append("</ul>");

            html.append("<h3>7. Confidence</h3><p><strong>")
                    .append(escape(analysis.getConfidence())).append("</strong></p>");
            html.append("</div>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
