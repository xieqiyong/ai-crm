package com.hz.crm.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeSearchRequest {

    private String query;

    private String category;

    private String sourceType;

    private Integer topK;

    private Integer vectorCandidates;

    private Integer keywordCandidates;

    private Integer databaseCandidates;

    private Double vectorWeight;

    private Double keywordWeight;

    private Double databaseWeight;

    private Double minScore;

    private Boolean databaseFallbackOnly;
}
