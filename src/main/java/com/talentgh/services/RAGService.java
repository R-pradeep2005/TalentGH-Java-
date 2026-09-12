package com.talentgh.services;

import java.util.ArrayList;
import java.util.List;

public class RAGService {
    
    public static class Document {
        private String content;
        private String file;
        private String type;
        private String repo;
        
        public Document(String content, String file, String type, String repo) {
            this.content = content;
            this.file = file;
            this.type = type;
            this.repo = repo;
        }
        
        public String getContent() { return content; }
        public String getFile() { return file; }
        public String getType() { return type; }
        public String getRepo() { return repo; }
    }
    
    public static class FileContent {
        private String path;
        private String content;
        
        public FileContent(String path, String content) {
            this.path = path;
            this.content = content;
        }
        
        public String getPath() { return path; }
        public String getContent() { return content; }
    }
    
    public List<Document> createDocuments(String repoName, String readme, List<FileContent> files) {
        List<Document> documents = new ArrayList<>();
        
        if (readme != null && !readme.isEmpty()) {
            String content = String.format("""
                Repository: %s
                
                File: README.md
                
                Type: Documentation
                
                Content:
                %s
                """, repoName, readme);
            
            documents.add(new Document(content, "README.md", "readme", repoName));
        }
        
        for (FileContent file : files) {
            String content = String.format("""
                Repository: %s
                
                File: %s
                
                Type: Source Code
                
                Code:
                %s
                """, repoName, file.getPath(), file.getContent());
            
            documents.add(new Document(content, file.getPath(), "source", repoName));
        }
        
        return documents;
    }
    
    public List<String> splitDocuments(List<Document> documents) {
        List<String> chunks = new ArrayList<>();
        int chunkSize = 800;
        int overlap = 60;
        
        for (Document doc : documents) {
            String content = doc.getContent();
            for (int i = 0; i < content.length(); i += (chunkSize - overlap)) {
                int end = Math.min(i + chunkSize, content.length());
                chunks.add(content.substring(i, end));
                
                if (end >= content.length()) {
                    break;
                }
            }
        }
        
        return chunks;
    }
    
    // Simplified version - in a real implementation, you would use a vector store
    public List<Document> retrieveRelevant(List<String> chunks, String query) {
        // For simplicity, return first few chunks as "relevant"
        // In production, you would use vector embeddings and similarity search
        List<Document> relevantDocs = new ArrayList<>();
        
        for (int i = 0; i < Math.min(2, chunks.size()); i++) {
            relevantDocs.add(new Document(chunks.get(i), "chunk_" + i, "chunk", "unknown"));
        }
        
        return relevantDocs;
    }
}
