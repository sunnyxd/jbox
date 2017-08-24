package com.alibaba.jbox.executor;

import java.util.concurrent.Callable;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/8/24 10:20:00.
 */
@FunctionalInterface
public interface AsyncCallable<V> extends Callable<V>, LoggerInter {

    V execute() throws Exception;

    @Override
    default V call() throws Exception {
        return execute();
    }

    default String taskInfo() {
        return this.getClass().getName();
    }
}
