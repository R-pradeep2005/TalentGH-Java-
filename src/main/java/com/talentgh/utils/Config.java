package com.talentgh.utils;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    private static final Dotenv dotenv = Dotenv.configure()
            .directory(".")
            .ignoreIfMissing()
            .load();

    public static String getGithubToken() {
        return resolve("GITHUB_TOKEN");
    }

    public static String getMistralApiKey() {
        return resolve("MISTRAL_API_KEY");
    }

    public static String getOpenaiApiKey() {
        return resolve("OPENAI_API_KEY");
    }

    private static String resolve(String key) {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value;
    }
}