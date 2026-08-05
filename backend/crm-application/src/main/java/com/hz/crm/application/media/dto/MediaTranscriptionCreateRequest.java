package com.hz.crm.application.media.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaTranscriptionCreateRequest {

    private Long businessId;

    private String fileName;

    private String contentType;

    private Long fileSize;

    private String storageKey;

    private String fileUrl;

    private String fileFormat;
}
