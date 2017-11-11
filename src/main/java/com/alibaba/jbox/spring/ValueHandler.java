package com.alibaba.jbox.spring;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/3 11:52:00.
 */
public interface ValueHandler {

    default Executor executor() {
        return null;
    }

    void handle(final ValueContext context);

    class ValueContext {

        private List<Field> changeFields = new ArrayList<>();

        private List<Object> changedValues = new ArrayList<>();

        public ValueContext() {
        }

        public ValueContext(List<Field> changeFields, List<Object> changedValues) {
            this.changeFields = changeFields;
            this.changedValues = changedValues;
        }

        public List<Field> getChangeFields() {
            return changeFields;
        }

        public List<Object> getChangedValues() {
            return changedValues;
        }
    }
}
