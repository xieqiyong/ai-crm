package com.hz.crm.wecom.service;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WecomUpsertResult<T> {

    private T data;

    private boolean created;
}
