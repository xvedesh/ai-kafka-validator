package com.analysis.failure.model;

public class FixProposal {
    private final String description;
    private final String filePath;
    private final Integer line;
    private final String currentText;
    private final String proposedText;

    public FixProposal(String description, String filePath, Integer line, String currentText, String proposedText) {
        this.description = description;
        this.filePath = filePath;
        this.line = line;
        this.currentText = currentText;
        this.proposedText = proposedText;
    }

    public String getDescription() {
        return description;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLine() {
        return line;
    }

    public String getCurrentText() {
        return currentText;
    }

    public String getProposedText() {
        return proposedText;
    }

    public boolean hasFileEdit() {
        return filePath != null && currentText != null && proposedText != null;
    }
}
