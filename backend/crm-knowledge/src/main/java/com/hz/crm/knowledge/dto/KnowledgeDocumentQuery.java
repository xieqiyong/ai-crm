package com.hz.crm.knowledge.dto;

import com.hz.crm.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeDocumentQuery extends PageQuery {

    private String keyword;

    private String sourceType;

    private String category;

    private String status;
}
