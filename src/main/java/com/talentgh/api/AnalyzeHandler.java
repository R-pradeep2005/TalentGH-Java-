package com.talentgh.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.talentgh.models.Repository;
import com.talentgh.models.Requirements;
import com.talentgh.services.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyzeHandler implements HttpHandler {
    private final Gson gson = new Gson();
    private final GitHubService githubService = new GitHubService();
    private final JDService jdService = new JDService();
    private final RepositoryRanker repositoryRanker = new RepositoryRanker();
    private final RepositoryService repositoryService = new RepositoryService();
    private final RAGService ragService = new RAGService();
    private final AnalyzerService analyzerService = new AnalyzerService();
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        if ("OPTIONS".equals(method)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(200, 0);
            return;
        }
        
        if (!"POST".equals(method)) {
            exchange.sendResponseHeaders(405, 0);
            return;
        }
        
        try {
            // Parse multipart form data
            Map<String, byte[]> formData = parseMultipartFormData(exchange);
            
            // Check for text fields (new frontend) or file uploads (old frontend)
            String githubUsername = null;
            String jdText = null;
            
            if (formData.containsKey("github_username")) {
                // New frontend: GitHub username as text field
                githubUsername = new String(formData.get("github_username"), "UTF-8").trim();
            } else if (formData.containsKey("resume")) {
                // Old frontend: Extract username from resume
                String resumeText = FileService.extractText(formData.get("resume"), "resume.pdf");
                githubUsername = githubService.extractUsername(resumeText);
            }
            
            if (formData.containsKey("job_description_text")) {
                // New frontend: Job description as text field
                jdText = new String(formData.get("job_description_text"), "UTF-8");
            } else if (formData.containsKey("job_description")) {
                // Old frontend: Extract text from job description file
                jdText = FileService.extractText(formData.get("job_description"), "jd.txt");
            }
            
            if (githubUsername == null || githubUsername.isEmpty()) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing required field: github_username. Please provide a GitHub username.");
                sendResponse(exchange, 400, error.toString());
                return;
            }
            
            if (jdText == null || jdText.isEmpty()) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing required field: job_description_text. Please provide a job description.");
                sendResponse(exchange, 400, error.toString());
                return;
            }
            
            // Fetch repositories
            List<Repository> repositories = githubService.fetchRepositories(githubUsername);
            
            // Extract requirements
            Requirements requirements = null;
            try {
                requirements = jdService.extractRequirements(jdText);
            } catch (Exception e) {
                System.err.println("Error extracting requirements: " + e.getMessage());
                // Create empty requirements if extraction fails
                requirements = new Requirements();
            }
            
            // Rank repositories
            List<Repository> rankedRepos = repositoryRanker.rankRepositories(repositories, requirements);
            Repository topRepo = rankedRepos.get(0);
            
            // Get repository tree
            com.google.gson.JsonArray tree = repositoryService.getRepositoryTree(githubUsername, topRepo.getName());
            List<String> files = repositoryService.filterSourceFiles(tree);
            
            // Download important files
            List<RAGService.FileContent> contents = new ArrayList<>();
            String[] validExtensions = {".py", ".js", ".ts", ".tsx", ".java", ".go", ".cpp", ".c", ".cs"};
            String[] importantNames = {"main", "app", "server", "index", "api", "auth", "controller", "service", "model", "config"};
            
            for (String path : files) {
                boolean hasValidExtension = Arrays.stream(validExtensions).anyMatch(path::endsWith);
                boolean hasImportantName = Arrays.stream(importantNames).anyMatch(name -> path.toLowerCase().contains(name));
                
                if (!hasValidExtension || !hasImportantName) {
                    continue;
                }
                
                System.out.println("Downloading: " + path);
                
                try {
                    String content = repositoryService.downloadFile(githubUsername, topRepo.getName(), path);
                    if (content != null && !content.isEmpty()) {
                        contents.add(new RAGService.FileContent(path, content.substring(0, Math.min(3000, content.length()))));
                    }
                } catch (Exception e) {
                    System.err.println("Error downloading file: " + path + " - " + e.getMessage());
                }
                
                if (contents.size() >= 5) {
                    break;
                }
            }
            
            // Get README
            String readme = "";
            try {
                readme = repositoryService.getReadme(githubUsername, topRepo.getName());
            } catch (Exception e) {
                System.err.println("Error getting README: " + e.getMessage());
            }
            
            // Create documents
            List<RAGService.Document> documents = ragService.createDocuments(topRepo.getName(), readme, contents);
            
            // Split documents
            List<String> chunks = ragService.splitDocuments(documents);
            
            // Retrieve relevant chunks (simplified)
            List<RAGService.Document> relevantDocs = ragService.retrieveRelevant(chunks, "");
            
            // Analyze
            Map<String, Object> analysis = null;
            try {
                analysis = analyzerService.analyze(jdText, relevantDocs);
            } catch (Exception e) {
                System.err.println("Error during analysis: " + e.getMessage());
                analysis = new HashMap<>();
                analysis.put("error", "Analysis failed: " + e.getMessage());
            }
            
            // Build response
            JsonObject response = new JsonObject();
            response.addProperty("repository", topRepo.getName());
            response.add("analysis", gson.toJsonTree(analysis));
            
            sendResponse(exchange, 200, response.toString());
            
        } catch (Exception e) {
            e.printStackTrace();
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            sendResponse(exchange, 500, error.toString());
        }
    }
    
    private Map<String, byte[]> parseMultipartFormData(HttpExchange exchange) throws IOException {
        Map<String, byte[]> formData = new HashMap<>();
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        
        System.out.println("Content-Type: " + contentType);
        
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            String boundary = contentType.split("boundary=")[1];
            System.out.println("Boundary: " + boundary);
            
            InputStream is = exchange.getRequestBody();
            byte[] data = is.readAllBytes();
            System.out.println("Request body size: " + data.length + " bytes");
            
            // Simple parsing - in production, use a proper multipart parser
            String content = new String(data, "UTF-8");
            String[] parts = content.split("--" + boundary);
            System.out.println("Number of parts: " + parts.length);
            
            for (int partIndex = 0; partIndex < parts.length; partIndex++) {
                String part = parts[partIndex];
                System.out.println("Part " + partIndex + " length: " + part.length());
                System.out.println("Part " + partIndex + " preview: " + part.substring(0, Math.min(100, part.length())));
                System.out.println("Part " + partIndex + " contains Content-Disposition: " + part.contains("Content-Disposition"));
                
                if (part.contains("Content-Disposition")) {
                    String[] lines = part.split("\r\n");
                    System.out.println("Number of lines in part: " + lines.length);
                    String name = null;
                    int blankLineIndex = -1;
                    
                    for (int i = 0; i < lines.length; i++) {
                        System.out.println("Line " + i + ": " + lines[i]);
                        if (lines[i].contains("name=")) {
                            try {
                                name = lines[i].split("name=\"")[1].split("\"")[0];
                                System.out.println("Found name: " + name);
                            } catch (Exception e) {
                                System.err.println("Error parsing name from line: " + lines[i]);
                            }
                        }
                        // Only treat line as blank if it comes after we've found the name
                        if (lines[i].isEmpty() && name != null) {
                            blankLineIndex = i;
                            System.out.println("Found blank line at index: " + i);
                            break;
                        }
                    }
                    
                    if (name != null && blankLineIndex != -1) {
                        System.out.println("Blank line index: " + blankLineIndex);
                        System.out.println("Total lines: " + lines.length);
                        System.out.println("Lines after blank line:");
                        for (int i = blankLineIndex + 1; i < lines.length; i++) {
                            System.out.println("  Line " + i + ": [" + lines[i] + "]");
                        }
                        
                        StringBuilder fileContent = new StringBuilder();
                        // Get content from the line after blank line to the end (excluding empty trailing lines)
                        for (int i = blankLineIndex + 1; i < lines.length; i++) {
                            if (!lines[i].isEmpty()) {
                                fileContent.append(lines[i]);
                                if (i < lines.length - 1 && !lines[i + 1].isEmpty()) {
                                    fileContent.append("\r\n");
                                }
                            }
                        }
                        // Trim to remove trailing whitespace
                        String contentStr = fileContent.toString().trim();
                        byte[] contentBytes = contentStr.getBytes("UTF-8");
                        formData.put(name, contentBytes);
                        System.out.println("Parsed field: " + name + " with size: " + contentBytes.length + " bytes");
                        System.out.println("Field content preview: " + contentStr.substring(0, Math.min(50, contentStr.length())));
                    }
                }
            }
        } else {
            System.err.println("Invalid or missing Content-Type: " + contentType);
        }
        
        System.out.println("Total parsed fields: " + formData.size());
        System.out.println("Field names: " + formData.keySet());
        
        return formData;
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}
