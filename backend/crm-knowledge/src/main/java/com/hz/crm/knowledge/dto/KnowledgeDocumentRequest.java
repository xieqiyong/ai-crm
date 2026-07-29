package com.hz.crm.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeDocumentRequest {

    private Long id;

    @NotBlank(message = "知识标题不能为空")
    private String title;

    private String sourceType;

    private String category;

    private String tags;

    private String sourceUrl;

    private String objectKey;

    private String sourceKey;

    private String rawFileHash;

    private String content;
}
