package com.hz.crm.common.api;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageData<T> {

    private long total;

    private int pageNo;

    private int pageSize;

    private List<T> records = new ArrayList<T>();

    public static <T> PageData<T> of(long total, int pageNo, int pageSize, List<T> records) {
        PageData<T> data = new PageData<T>();
        data.setTotal(total);
        data.setPageNo(pageNo);
        data.setPageSize(pageSize);
        data.setRecords(records);
        return data;
    }
}
