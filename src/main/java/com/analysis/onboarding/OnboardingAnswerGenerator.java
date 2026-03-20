package com.analysis.onboarding;

import com.analysis.onboarding.model.AnswerGenerationContext;

public interface OnboardingAnswerGenerator {
    boolean isAvailable();

    String modeDescription();

    String generate(AnswerGenerationContext context) throws Exception;
}
