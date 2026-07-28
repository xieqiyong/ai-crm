package com.hz.crm.knowledge.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeIngestEventResponse {

    private String id;

    private String taskId;

    private String documentId;

    private String stage;

    private String status;

    private String message;

    private String detailJson;

    private Long elapsedMs;

    private LocalDateTime createdAt;
}
