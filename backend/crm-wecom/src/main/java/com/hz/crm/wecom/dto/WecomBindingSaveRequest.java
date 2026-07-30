package com.hz.crm.wecom.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WecomBindingSaveRequest {

    private Long configId;

    private List<WecomBindingSaveItem> bindings;
}
