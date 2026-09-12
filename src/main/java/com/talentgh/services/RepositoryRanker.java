package com.talentgh.services;

import com.talentgh.models.Repository;
import com.talentgh.models.Requirements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RepositoryRanker {

    public List<Repository> rankRepositories(List<Repository> repositories, Requirements requirements) {
        List<String> keywords = new ArrayList<>();

        if (requirements != null) {
            keywords.addAll(safe(requirements.getLanguages()));
            keywords.addAll(safe(requirements.getFrameworks()));
            keywords.addAll(safe(requirements.getDatabases()));
            keywords.addAll(safe(requirements.getTools()));
            keywords.addAll(safe(requirements.getConcepts()));
        }

        keywords = keywords.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        for (Repository repo : repositories) {
            int score = 0;

            String searchable = String.join(" ",
                    repo.getName() != null ? repo.getName() : "",
                    repo.getDescription() != null ? repo.getDescription() : "",
                    repo.getLanguage() != null ? repo.getLanguage() : "",
                    repo.getTopics() != null ? String.join(" ", repo.getTopics()) : ""
            ).toLowerCase();

            for (String keyword : keywords) {
                if (searchable.contains(keyword)) {
                    score += 10;
                }
            }

            repo.setScore(score);
        }

        repositories.sort(Comparator.comparingInt(Repository::getScore).reversed());

        return repositories.stream().limit(3).collect(Collectors.toList());
    }

    /**
     * Returns an empty list instead of null so addAll() never throws,
     * regardless of what the requirements-extraction step produced.
     */
    private List<String> safe(List<String> list) {
        return list != null ? list : Collections.emptyList();
    }
}