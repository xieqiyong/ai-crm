package com.hz.crm.knowledge.support;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeTextChunk {

    private Integer chunkIndex;

    private String content;

    private Integer tokenEstimate;
}
