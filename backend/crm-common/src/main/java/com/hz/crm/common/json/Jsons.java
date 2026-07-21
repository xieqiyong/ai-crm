package com.hz.crm.common.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

public final class Jsons {

    private Jsons() {
    }

    public static String toJson(Object value) {
        return JSON.toJSONString(value);
    }

    public static <T> T parseObject(String text, Class<T> type) {
        if (text == null || text.trim().length() == 0) {
            return null;
        }
        return JSON.parseObject(text, type);
    }

    public static <T> T parseObject(String text, TypeReference<T> typeReference) {
        if (text == null || text.trim().length() == 0) {
            return null;
        }
        return JSON.parseObject(text, typeReference);
    }
}
