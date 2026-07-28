package com.hz.crm.knowledge.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeIngestTaskResponse {

    private String id;

    private String documentId;

    private Boolean force;

    private String status;

    private String stage;

    private Integer progress;

    private String message;

    private String errorMessage;

    private Integer indexVersion;

    private String indexHash;

    private Integer chunkCount;

    private Integer vectorDimension;

    private String embeddingModel;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<KnowledgeIngestEventResponse> events = new ArrayList<KnowledgeIngestEventResponse>();
}
