package com.alibaba.jbox.script;

/**
 * @author jifang@alibaba-inc.com
 * @version 1.1
 * @since jbox 1.4.0 (2016/11/19 上午9:20).
 */
public enum ScriptType {

    Groovy("Groovy"),

    JavaScript("JavaScript");

    ScriptType(String type) {
        this.type = type;
    }

    private String type;

    public String getValue() {
        return this.type;
    }
}
