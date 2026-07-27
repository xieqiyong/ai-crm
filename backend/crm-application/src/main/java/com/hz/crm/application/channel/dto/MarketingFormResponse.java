package com.hz.crm.application.channel.dto;

import com.hz.crm.domain.channel.MarketingFormStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarketingFormResponse {

    private Long id;

    private Long tenantId;

    private String formCode;

    private String title;

    private String description;

    private String source;

    private String submitMessage;

    private MarketingFormStatus status;

    private boolean autoCreateLead;

    private Long ownerId;

    private Long viewCount;

    private Long submitCount;

    private String publicPath;

    private List<MarketingFormFieldResponse> fields = new ArrayList<MarketingFormFieldResponse>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
