package com.talentgh.models;

import java.util.List;

public class Requirements {
    private List<String> languages;
    private List<String> frameworks;
    private List<String> databases;
    private List<String> tools;
    private List<String> concepts;
    
    public Requirements() {}
    
    // Getters and Setters
    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }
    
    public List<String> getFrameworks() { return frameworks; }
    public void setFrameworks(List<String> frameworks) { this.frameworks = frameworks; }
    
    public List<String> getDatabases() { return databases; }
    public void setDatabases(List<String> databases) { this.databases = databases; }
    
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
    
    public List<String> getConcepts() { return concepts; }
    public void setConcepts(List<String> concepts) { this.concepts = concepts; }
}
