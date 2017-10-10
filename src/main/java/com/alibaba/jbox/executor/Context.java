package com.alibaba.jbox.executor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/10/10 11:36:00.
 */
class Context {

    private Thread parent;

    private String group;

    private Map<String, Object> extContext;

    Context(Thread parent, String group) {
        this.parent = parent;
        this.group = group;
    }

    public Thread getParent() {
        return parent;
    }

    public void setParent(Thread parent) {
        this.parent = parent;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void putExtContext(String key, Object value) {
        if (extContext == null) {
            extContext = new HashMap<>();
        }
        extContext.put(key, value);
    }

    public Map<String, Object> getExtContext() {
        return extContext;
    }
}
