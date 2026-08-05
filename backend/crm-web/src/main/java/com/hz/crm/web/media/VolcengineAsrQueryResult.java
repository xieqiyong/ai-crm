package com.hz.crm.web.media;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VolcengineAsrQueryResult {

    private boolean finished;

    private boolean processing;

    private String transcriptText;

    private String utterancesJson;

    private String rawResultJson;

    private String errorMessage;
}
