package com.hz.crm.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeIngestResponse {

    private String taskId;

    private String documentId;

    private Boolean asyncTask;

    private String status;

    private String stage;

    private Integer progress;

    private String vectorStatus;

    private Integer chunkCount;

    private Integer vectorDimension;

    private String embeddingModel;

    private Integer indexVersion;

    private String indexHash;

    private Boolean skipped;

    private String message;
}
