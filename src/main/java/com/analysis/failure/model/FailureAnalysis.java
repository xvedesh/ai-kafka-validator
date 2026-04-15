package com.analysis.failure.model;

import com.analysis.failure.FailureCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FailureAnalysis {
    private final FailureEvidence evidence;
    private final FailureCategory category;
    private final String likelyLayer;
    private final String shortExplanation;
    private final String whatHappened;
    private final String rootCause;
    private final List<String> evidencePoints;
    private final List<CodeReference> codeReferences;
    private final List<String> nextChecks;
    private final String confidence;
    private final List<FixProposal> fixProposals;

    public FailureAnalysis(FailureEvidence evidence, FailureCategory category, String likelyLayer, String shortExplanation,
                           String whatHappened, String rootCause, List<String> evidencePoints,
                           List<CodeReference> codeReferences, List<String> nextChecks, String confidence,
                           List<FixProposal> fixProposals) {
        this.evidence = evidence;
        this.category = category;
        this.likelyLayer = likelyLayer;
        this.shortExplanation = shortExplanation;
        this.whatHappened = whatHappened;
        this.rootCause = rootCause;
        this.evidencePoints = new ArrayList<>(evidencePoints);
        this.codeReferences = new ArrayList<>(codeReferences);
        this.nextChecks = new ArrayList<>(nextChecks);
        this.confidence = confidence;
        this.fixProposals = new ArrayList<>(fixProposals);
    }

    public FailureEvidence getEvidence() {
        return evidence;
    }

    public FailureCategory getCategory() {
        return category;
    }

    public String getLikelyLayer() {
        return likelyLayer;
    }

    public String getShortExplanation() {
        return shortExplanation;
    }

    public String getWhatHappened() {
        return whatHappened;
    }

    public String getRootCause() {
        return rootCause;
    }

    public List<String> getEvidencePoints() {
        return Collections.unmodifiableList(evidencePoints);
    }

    public List<CodeReference> getCodeReferences() {
        return Collections.unmodifiableList(codeReferences);
    }

    public List<String> getNextChecks() {
        return Collections.unmodifiableList(nextChecks);
    }

    public String getConfidence() {
        return confidence;
    }

    public List<FixProposal> getFixProposals() {
        return Collections.unmodifiableList(fixProposals);
    }
}
