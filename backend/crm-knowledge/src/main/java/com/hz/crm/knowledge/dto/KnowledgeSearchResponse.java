package com.hz.crm.knowledge.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeSearchResponse {

    private String query;

    private Integer topK;

    private boolean embeddingEnabled;

    private boolean milvusEnabled;

    private boolean elasticsearchEnabled;

    private String searchMode;

    private Integer vectorCandidates;

    private Integer keywordCandidates;

    private Integer databaseCandidates;

    private Double vectorWeight;

    private Double keywordWeight;

    private Double databaseWeight;

    private Double minScore;

    private boolean databaseFallbackUsed;

    private String message;

    private List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
}
