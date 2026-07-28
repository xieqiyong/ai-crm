package com.hz.crm.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeSearchHit {

    private String chunkId;

    private String documentId;

    private String title;

    private String category;

    private String sourceType;

    private String sourceUrl;

    private String content;

    private Integer indexVersion;

    private Double score;

    private String matchType;

    private Double hybridScore;

    private Double vectorScore;

    private Double keywordScore;

    private Double databaseScore;

    private String matchChannels;
}
