package com.talentgh.models;

public class Repository {
    private String name;
    private String description;
    private String language;
    private String[] topics;
    private int stars;
    private String updatedAt;
    private int score;
    
    public Repository() {}
    
    public Repository(String name, String description, String language, String[] topics, int stars, String updatedAt) {
        this.name = name;
        this.description = description;
        this.language = language;
        this.topics = topics;
        this.stars = stars;
        this.updatedAt = updatedAt;
    }
    
    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public String[] getTopics() { return topics; }
    public void setTopics(String[] topics) { this.topics = topics; }
    
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}
