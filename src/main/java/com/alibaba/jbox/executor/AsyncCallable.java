package com.alibaba.jbox.executor;

import java.util.concurrent.Callable;

import com.google.common.base.Strings;
import com.taobao.eagleeye.EagleEye;
import org.slf4j.MDC;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/8/24 10:20:00.
 */
@FunctionalInterface
public interface AsyncCallable<V> extends Callable<V>, ExecutorLoggerInter {

    V execute() throws Exception;

    @Override
    default V call() throws Exception {
        Object rpcContext = EagleEye.currentRpcContext();
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            return execute();
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
            EagleEye.clearRpcContext();
        }
    }

    default String taskInfo() {
        return this.getClass().getName();
    }
}
