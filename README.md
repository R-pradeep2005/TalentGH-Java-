# Talent Acquisition GitHub Analyzer - Java Implementation

A Java-based system for analyzing candidate resumes using LLM and GitHub profile data, implemented without Spring Boot using standard Java HTTP server and client libraries.

## Features

- Upload multiple resumes (PDF, DOCX, TXT)
- Extract GitHub usernames from resumes
- Fetch and analyze GitHub repositories
- Use LLM (Mistral) to select relevant repositories based on job description
- Deep-dive analysis of selected repos with scoring in multiple categories
- Output structured analysis for HR decision-making

## Technical Stack

- **Java 21** - Modern Java with latest features
- **Maven** - Build tool and dependency management
- **Java HttpServer** - Built-in HTTP server (com.sun.net.httpserver)
- **Apache HttpClient 5** - HTTP client for external API calls
- **Apache POI** - DOCX file parsing
- **Apache PDFBox** - PDF file parsing
- **Gson** - JSON processing
- **Mistral AI** - LLM for analysis

## Installation

1. Ensure Java 21 is installed:
   ```bash
   java -version
   ```

2. Build the project:
   ```bash
   cd talentgh-java
   mvn clean package
   ```

3. Set up environment variables:
   ```bash
   export GITHUB_TOKEN=your_github_token
   export MISTRAL_API_KEY=your_mistral_api_key
   ```

## Usage

1. Run the server:
   ```bash
   java -jar target/talentgh-analyzer-1.0.0.jar
   ```
   
   Or using Maven:
   ```bash
   mvn exec:java
   ```

Note: The JAR file created by the Maven Shade Plugin includes all dependencies, so you can run it directly without needing to manage classpath dependencies.

2. The API will be available at `http://localhost:8000`

3. Use the `/analyze` endpoint with:
   - `resume`: Resume file (PDF, DOCX, or TXT)
   - `job_description`: Job description file (PDF, DOCX, or TXT)

## API Endpoints

### GET /
Returns API status:
```json
{
  "message": "API is running"
}
```

### POST /analyze
Analyzes a resume against a job description:
- **Request**: Multipart form data with `resume` and `job_description` files
- **Response**: JSON analysis with repository information and scoring

## Project Structure

```
talentgh-java/
├── src/main/java/com/talentgh/
│   ├── Main.java                 # Application entry point
│   ├── api/
│   │   ├── AnalyzeHandler.java  # POST /analyze endpoint
│   │   └── RootHandler.java      # GET / endpoint
│   ├── models/
│   │   ├── Repository.java       # Repository data model
│   │   └── Requirements.java     # Job requirements model
│   ├── services/
│   │   ├── AnalyzerService.java  # LLM-based code analysis
│   │   ├── FileService.java      # File parsing (PDF, DOCX, TXT)
│   │   ├── GitHubService.java    # GitHub API client
│   │   ├── JDService.java        # Job description analysis
│   │   ├── RAGService.java       # Document chunking and retrieval
│   │   ├── RepositoryRanker.java # Repository ranking logic
│   │   └── RepositoryService.java # Repository file operations
│   └── utils/
│       └── Config.java           # Configuration and environment variables
└── pom.xml                       # Maven configuration
```

## Key Differences from Python Version

- **No Spring Boot**: Uses Java's built-in HttpServer
- **No FastAPI**: Uses custom HttpHandler implementations
- **No LangChain**: Direct HTTP calls to Mistral API
- **Manual multipart parsing**: Simplified multipart form data handling
- **No vector store**: Simplified RAG implementation (can be enhanced with proper vector embeddings)

## Requirements

- Java 21
- Maven 3.6+
- GitHub Personal Access Token (for API access)
- Mistral API Key (for LLM analysis)

## Future Enhancements

- Add proper vector store implementation (e.g., Weaviate, Milvus)
- Improve multipart form data parsing
- Add comprehensive error handling
- Implement proper logging configuration
- Add unit and integration tests
- Docker support for containerization
