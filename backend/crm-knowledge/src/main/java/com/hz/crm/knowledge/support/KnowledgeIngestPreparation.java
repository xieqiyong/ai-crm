package com.hz.crm.knowledge.support;

import com.hz.crm.knowledge.domain.KnowledgeIngestTaskEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KnowledgeIngestPreparation {

    private KnowledgeIngestTaskEntity task;

    private boolean scheduled;

    private boolean skipped;
}
