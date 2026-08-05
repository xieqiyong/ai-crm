package com.hz.crm.application.media.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaTranscriptionResponse {

    private Long id;

    private Long tenantId;

    private String businessType;

    private Long businessId;

    private String targetType;

    private Long targetId;

    private String targetName;

    private String provider;

    private String status;

    private String providerTaskId;

    private String language;

    private String fileName;

    private String contentType;

    private Long fileSize;

    private String fileUrl;

    private String fileFormat;

    private String audioFileName;

    private String audioFileUrl;

    private String audioFileFormat;

    private Integer progress;

    private String errorMessage;

    private String transcriptText;

    private LocalDateTime submittedAt;

    private LocalDateTime finishedAt;

    private Long ownerId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
