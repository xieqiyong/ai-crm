package com.hz.crm.application.lead.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadImportResult {

    private int totalCount;

    private int importedCount;

    private int skippedCount;

    private int failedCount;

    private List<LeadImportError> errors = new ArrayList<LeadImportError>();
}
