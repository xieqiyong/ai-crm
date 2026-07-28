package com.hz.crm.web.attachment;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachmentUploadResponse {

    private String fileName;

    private String contentType;

    private Long size;

    private String storageKey;

    private String url;
}
