package com.hz.crm.knowledge.domain;

public enum KnowledgeDocumentVersionStatus {
    CREATED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    INDEXING,
    READY,
    ACTIVE,
    FAILED,
    SUPERSEDED,
    RETIRED
}
