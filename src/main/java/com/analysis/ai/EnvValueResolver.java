package com.analysis.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EnvValueResolver {
    private static Map<String, String> dotEnvValues;

    private EnvValueResolver() {
    }

    public static String firstNonBlank(List<String> systemProperties, List<String> envKeys, String defaultValue) {
        for (String property : systemProperties) {
            String value = System.getProperty(property);
            if (hasText(value)) {
                return value.trim();
            }
        }

        for (String envKey : envKeys) {
            String value = System.getenv(envKey);
            if (hasText(value)) {
                return value.trim();
            }
        }

        Map<String, String> dotEnv = loadDotEnv();
        for (String envKey : envKeys) {
            String value = dotEnv.get(envKey);
            if (hasText(value)) {
                return value.trim();
            }
        }

        return defaultValue == null ? "" : defaultValue;
    }

    private static synchronized Map<String, String> loadDotEnv() {
        if (dotEnvValues != null) {
            return dotEnvValues;
        }

        Map<String, String> values = new HashMap<>();
        Path dotEnvPath = Path.of("").toAbsolutePath().normalize().resolve(".env");
        if (!Files.exists(dotEnvPath)) {
            dotEnvValues = values;
            return dotEnvValues;
        }

        try {
            List<String> lines = Files.readAllLines(dotEnvPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int delimiter = trimmed.indexOf("=");
                String key = trimmed.substring(0, delimiter).trim();
                String value = trimmed.substring(delimiter + 1).trim();
                values.put(key, stripQuotes(value));
            }
        } catch (IOException ignored) {
            // Fall back to system environment only if the file cannot be read.
        }

        dotEnvValues = values;
        return dotEnvValues;
    }

    private static String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == 34 && last == 34) || (first == 39 && last == 39)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
