package com.talentgh.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.talentgh.utils.Config;
import com.talentgh.utils.MistralThrottle;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AnalyzerService {
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int MAX_RETRIES = 3;
    private final Gson gson = new Gson();

    public Map<String, Object> analyze(String jobDescription, java.util.List<RAGService.Document> documents) throws IOException, ParseException {
        StringBuilder code = new StringBuilder();

        for (RAGService.Document doc : documents) {
            code.append(String.format("""
                File: %s

                %s

                ------------------------
                """, doc.getFile(), doc.getContent().substring(0, Math.min(600, doc.getContent().length()))));
        }

        String prompt = "You are a senior software engineer performing a GitHub repository review.\n\n" +
                "Job Description:\n\n" + jobDescription + "\n\n" +
                "Relevant Repository Snippets:\n\n" + code + "\n\n" +
                "Return ONLY valid JSON.\n" +
                "{\n" +
                "    \"score\": 0,\n" +
                "    \"matching_skills\": [],\n" +
                "    \"missing_skills\": [],\n" +
                "    \"strengths\": [],\n" +
                "    \"weaknesses\": [],\n" +
                "    \"evidence\": [\n" +
                "        {\n" +
                "            \"file\": \"\",\n" +
                "            \"reason\": \"\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"summary\": \"\"\n" +
                "}";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "openai/gpt-oss-120b"); 
        requestBody.addProperty("temperature", 0);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);

        return executeWithRetry(requestBody, 0);
    }

    private Map<String, Object> executeWithRetry(JsonObject requestBody, int attempt) throws IOException, ParseException {
        // Ensure at least ~1.1s since the last Mistral call from ANY thread/service before firing this one.
        MistralThrottle.waitForSlot();

        // IMPORTANT: disable HttpClient5's own built-in automatic retry (maxRetries = 0).
        // Without this, HttpClient5 silently retries 429s internally, doubling our
        // real request volume and bypassing MistralThrottle entirely.
        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(0, TimeValue.ofSeconds(1)))
                .build()) {

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
                        System.err.println("Mistral rate limit exceeded after " + MAX_RETRIES + " retries. Returning empty result.");
                        Map<String, Object> fallback = new HashMap<>();
                        fallback.put("score", 0);
                        fallback.put("summary", "Analysis unavailable: rate limited by Mistral API.");
                        return fallback;
                    }
                    long waitMs = 2000L * (attempt + 1);
                    String retryAfter = response.getFirstHeader("Retry-After") != null
                            ? response.getFirstHeader("Retry-After").getValue()
                            : null;
                    if (retryAfter != null) {
                        try {
                            waitMs = Long.parseLong(retryAfter) * 1000L;
                        } catch (NumberFormatException ignored) { }
                    }
                    System.out.println("Mistral 429, retrying in " + waitMs + "ms (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry backoff", ie);
                    }
                    return executeWithRetry(requestBody, attempt + 1);
                }

                if (status != 200) {
                    throw new IOException("Mistral API Error: " + status + " - " + responseBody);
                }

                JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
                String content = responseJson.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();

                content = content.replace("```json", "").replace("```", "").trim();

                try {
                    Map<String, Object> result = gson.fromJson(content, Map.class);
                    return result != null ? result : new HashMap<>();
                } catch (Exception e) {
                    Map<String, Object> fallback = new HashMap<>();
                    fallback.put("raw", content);
                    return fallback;
                }
            }
        }
    }
}