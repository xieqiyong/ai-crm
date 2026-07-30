package com.hz.crm.application.mail.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MailLogQuery {

    private Integer pageNo;

    private Integer pageSize;

    public int safePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
