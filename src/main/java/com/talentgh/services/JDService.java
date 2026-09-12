package com.talentgh.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.talentgh.models.Requirements;
import com.talentgh.utils.Config;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JDService {
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int MAX_RETRIES = 3;
    private final Gson gson = new Gson();

    public Requirements extractRequirements(String jdText) throws IOException, ParseException {
        String prompt = "You are an expert technical recruiter.\n\n" +
                "Extract the important technical requirements from this Job Description.\n\n" +
                "Job Description:\n" + jdText + "\n\n" +
                "Return ONLY valid JSON in this format:\n" +
                "{\n" +
                "    \"languages\": [],\n" +
                "    \"frameworks\": [],\n" +
                "    \"databases\": [],\n" +
                "    \"tools\": [],\n" +
                "    \"concepts\": []\n" +
                "}";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "openai/gpt-oss-20b"); 
        requestBody.addProperty("temperature", 0);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);

        Requirements result = executeWithRetry(requestBody, 0);
        return sanitize(result);
    }

    private Requirements executeWithRetry(JsonObject requestBody, int attempt) throws IOException, ParseException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://api.groq.com/openai/v1/chat/completions");
            request.setHeader("Authorization", "Bearer " + Config.getMistralApiKey());
            request.setHeader("Content-Type", "application/json");
            request.setHeader("User-Agent", "mistralai-python/1.7.0");
            request.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity);
                int status = response.getCode();

                if (status == 429) {
                    if (attempt >= MAX_RETRIES) {
                        System.err.println("JDService: Mistral rate limit exceeded after " + MAX_RETRIES + " retries. Returning empty requirements.");
                        return null; // sanitize() will fill in empty lists
                    }
                    long waitMs = 1500L * (attempt + 1);
                    if (response.getFirstHeader("Retry-After") != null) {
                        try {
                            waitMs = Long.parseLong(response.getFirstHeader("Retry-After").getValue()) * 1000L;
                        } catch (NumberFormatException ignored) { }
                    }
                    System.out.println("JDService: Mistral 429, retrying in " + waitMs + "ms (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry backoff", ie);
                    }
                    return executeWithRetry(requestBody, attempt + 1);
                }

                if (status != 200) {
                    System.err.println("JDService: Mistral API Error " + status + " - " + responseBody);
                    return null; // sanitize() will fill in empty lists rather than crashing the pipeline
                }

                JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
                String content = responseJson.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();

                content = content.replace("```json", "").replace("```", "").trim();

                try {
                    return gson.fromJson(content, Requirements.class);
                } catch (Exception e) {
                    System.err.println("JDService: Failed to parse Mistral response as Requirements: " + e.getMessage());
                    return null;
                }
            }
        }
    }

    /**
     * Guarantees a non-null Requirements object with non-null lists,
     * regardless of what Mistral returned (rate limited, malformed, or missing fields).
     */
    private Requirements sanitize(Requirements requirements) {
        Requirements safe = requirements != null ? requirements : new Requirements();

        if (safe.getLanguages() == null) safe.setLanguages(new ArrayList<>());
        if (safe.getFrameworks() == null) safe.setFrameworks(new ArrayList<>());
        if (safe.getDatabases() == null) safe.setDatabases(new ArrayList<>());
        if (safe.getTools() == null) safe.setTools(new ArrayList<>());
        if (safe.getConcepts() == null) safe.setConcepts(new ArrayList<>());

        return safe;
    }
}