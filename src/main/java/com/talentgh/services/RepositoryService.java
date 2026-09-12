package com.talentgh.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.talentgh.utils.Config;
import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RepositoryService {
    private static final String GITHUB_API = "https://api.github.com";
    private final Gson gson = new Gson();
    
    private static final String[] VALID_EXTENSIONS = {".py", ".js", ".ts", ".tsx", ".java", ".go", ".cpp", ".c", ".cs"};
    private static final String[] IMPORTANT_NAMES = {"main", "app", "server", "index", "api", "auth", "controller", "service", "model", "config"};
    private static final String[] IGNORED_FOLDERS = {"node_modules/", ".git/", "__pycache__/", "dist/", "build/", "venv/"};
    
    public String getDefaultBranch(String owner, String repo) throws IOException, ParseException {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo;
        
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
                
                JsonObject repoJson = gson.fromJson(responseBody, JsonObject.class);
                return repoJson.get("default_branch").getAsString();
            }
        }
    }
    
    public JsonArray getRepositoryTree(String owner, String repo) throws IOException, ParseException {
        String branch = getDefaultBranch(owner, repo);
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1";
        
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
                
                JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
                return responseJson.getAsJsonArray("tree");
            }
        }
    }
    
    public List<String> filterSourceFiles(JsonArray tree) {
        List<String> files = new ArrayList<>();
        
        for (int i = 0; i < tree.size(); i++) {
            JsonObject item = tree.get(i).getAsJsonObject();
            
            if (!"blob".equals(item.get("type").getAsString())) {
                continue;
            }
            
            String path = item.get("path").getAsString();
            
            if (Arrays.stream(IGNORED_FOLDERS).anyMatch(path::startsWith)) {
                continue;
            }
            
            if (Arrays.stream(VALID_EXTENSIONS).anyMatch(path::endsWith)) {
                files.add(path);
            }
        }
        
        return files;
    }
    
    public String downloadFile(String owner, String repo, String path) throws IOException, ParseException {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/contents/" + path;
        
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
                
                JsonObject data = gson.fromJson(responseBody, JsonObject.class);
                
                if ("base64".equals(data.get("encoding").getAsString())) {
                    String content = data.get("content").getAsString();
                    byte[] decoded = Base64.decodeBase64(content);
                    return new String(decoded, StandardCharsets.UTF_8);
                }
                
                return "";
            }
        }
    }
    
    public String getReadme(String owner, String repo) throws IOException, ParseException {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/readme";
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", "Bearer " + Config.getGithubToken());
            request.setHeader("Accept", "application/vnd.github+json");
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getCode() != 200) {
                    return "";
                }
                
                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity);
                
                JsonObject data = gson.fromJson(responseBody, JsonObject.class);
                String content = data.get("content").getAsString();
                byte[] decoded = Base64.decodeBase64(content);
                return new String(decoded, StandardCharsets.UTF_8);
            }
        }
    }
}
