package com.analysis.onboarding;

import com.analysis.onboarding.model.ProjectChunk;

import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class SetupOnboardingAgent {
    private static final String BANNER = "=== AI Kafka Validator - Setup / Onboarding Agent ===";

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        List<ProjectChunk> chunks = new OnboardingKnowledgeBase(projectRoot).load();
        SetupOnboardingService service = new SetupOnboardingService(projectRoot, chunks);

        String propertyQuestion = System.getProperty("agent.question", "").trim();
        if (!propertyQuestion.isEmpty()) {
            System.out.println(BANNER);
            System.out.println("Mode: " + service.modeDescription());
            System.out.println(service.answer(propertyQuestion));
            return;
        }

        if (args.length > 0) {
            String question = String.join(" ", args).trim();
            System.out.println(BANNER);
            System.out.println("Mode: " + service.modeDescription());
            System.out.println(service.answer(question));
            return;
        }

        System.out.println(BANNER);
        System.out.println("Mode: " + service.modeDescription());
        System.out.println("I can help you:");
        System.out.println("- understand how the framework works");
        System.out.println("- run it in Docker or locally");
        System.out.println("- troubleshoot setup issues");
        System.out.println("- find scenarios, tags, reports, and Kafka validations");
        System.out.println("- explain how to adapt it for enterprise environments");
        System.out.println();
        System.out.println("Try asking:");
        System.out.println("- How does this framework work?");
        System.out.println("- How do I run only negative scenarios?");
        System.out.println("- Why can't I run Kafka tests locally?");
        System.out.println("- How would I adapt this for a real Kafka cluster?");
        System.out.println("Type 'exit' to quit.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print(System.lineSeparator() + "question> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String question = scanner.nextLine().trim();
                if (question.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(question) || "quit".equalsIgnoreCase(question)) {
                    break;
                }
                System.out.println();
                System.out.println(service.answer(question));
            }
        }
    }
}
