package com.alibaba.jbox.spring;

import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/3 11:52:00.
 */
public interface FieldChangedCallback {

    Executor getExecutor();


    void receiveConfigInfo(List<Pair<Field, Object>> fieldWithValues);
}
