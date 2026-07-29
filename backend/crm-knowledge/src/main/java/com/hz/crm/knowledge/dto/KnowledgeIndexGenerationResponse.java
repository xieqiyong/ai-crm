package com.hz.crm.knowledge.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeIndexGenerationResponse {

    private String id;

    private String status;

    private String elasticsearchIndex;

    private String milvusCollection;

    private String embeddingModel;

    private Integer vectorDimension;

    private String chunkProfileHash;

    private String snapshotOutboxId;

    private String replayedOutboxId;

    private Integer documentCount;

    private Integer completedDocumentCount;

    private Integer progress;

    private String message;

    private String errorMessage;

    private LocalDateTime activatedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
