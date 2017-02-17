package com.vdian.jbox.utils;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Strings;

import java.util.Collections;

/**
 * @author jifang
 * @since 16/8/9 下午2:53.
 */
public class JsonAppender {

    private StringBuilder sb;

    public JsonAppender(String json) {
        if (Strings.isNullOrEmpty(json)) {
            this.sb = new StringBuilder();
        } else {
            this.sb = new StringBuilder(json);
        }
    }

    public JsonAppender append(String name, Object value) {
        if (sb.length() <= 0) {
            sb.append(JSON.toJSONString(Collections.singletonMap(name, value)));
        } else {
            int index = sb.lastIndexOf("}");
            String appendStr;
            if (value instanceof String) {
                appendStr = String.format(",\"%s\":\"%s\"", name, value);
            } else {
                appendStr = String.format(",\"%s\":%s", name, value);
            }
            sb.insert(index, appendStr);
        }
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
