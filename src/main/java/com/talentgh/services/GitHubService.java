package com.talentgh.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.talentgh.models.Repository;
import com.talentgh.utils.Config;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitHubService {
    private static final String GITHUB_API = "https://api.github.com";
    private final Gson gson = new Gson();
    
    public String extractUsername(String resumeText) {
        Pattern pattern = Pattern.compile("github\\.com/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(resumeText);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    public List<Repository> fetchRepositories(String username) throws IOException, ParseException {
        String url = GITHUB_API + "/users/" + username + "/repos?per_page=100&sort=updated";
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", "Bearer " + Config.getGithubToken());
            request.setHeader("Accept", "application/vnd.github+json");
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity);
                
                if (response.getCode() != 200) {
                    throw new IOException("GitHub Error: " + response.getCode());
                }
                
                JsonArray reposArray = gson.fromJson(responseBody, JsonArray.class);
                List<Repository> repositories = new ArrayList<>();
                
                for (int i = 0; i < reposArray.size(); i++) {
                    JsonObject repoJson = reposArray.get(i).getAsJsonObject();
                    
                    Repository repo = new Repository();
                    repo.setName(repoJson.get("name").getAsString());
                    
                    if (repoJson.has("description") && !repoJson.get("description").isJsonNull()) {
                        repo.setDescription(repoJson.get("description").getAsString());
                    }
                    
                    if (repoJson.has("language") && !repoJson.get("language").isJsonNull()) {
                        repo.setLanguage(repoJson.get("language").getAsString());
                    }
                    
                    if (repoJson.has("topics")) {
                        JsonArray topicsArray = repoJson.getAsJsonArray("topics");
                        String[] topics = new String[topicsArray.size()];
                        for (int j = 0; j < topicsArray.size(); j++) {
                            topics[j] = topicsArray.get(j).getAsString();
                        }
                        repo.setTopics(topics);
                    }
                    
                    repo.setStars(repoJson.get("stargazers_count").getAsInt());
                    repo.setUpdatedAt(repoJson.get("updated_at").getAsString());
                    
                    repositories.add(repo);
                }
                
                return repositories;
            }
        }
    }
}
