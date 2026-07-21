package com.hz.crm.common.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageQuery {

    private int pageNo = 1;

    private int pageSize = 20;

    public int safePageNo() {
        if (pageNo < 1) {
            return 1;
        }
        return pageNo;
    }

    public int safePageSize() {
        if (pageSize < 1) {
            return 20;
        }
        if (pageSize > 200) {
            return 200;
        }
        return pageSize;
    }
}
