package com.hz.crm.knowledge.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeDocumentResponse {

    private String id;

    private String title;

    private String sourceType;

    private String category;

    private String tags;

    private String sourceUrl;

    private String objectKey;

    private String content;

    private String status;

    private String vectorStatus;

    private Integer chunkCount;

    private Integer vectorDimension;

    private String embeddingModel;

    private Integer indexVersion;

    private String indexHash;

    private LocalDateTime indexedAt;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
