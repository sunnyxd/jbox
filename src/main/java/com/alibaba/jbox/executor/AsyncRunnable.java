package com.alibaba.jbox.executor;

import com.google.common.base.Strings;
import com.taobao.eagleeye.EagleEye;
import org.slf4j.MDC;

/**
 * 对Runnable的包装, 在Runnable进入task queue或task被reject时可以打印更详细的信息
 * 即使在executor.submit()时使用JDK原生的Runnable, 也会被封装成一个AsyncRunnable
 *
 * @author jifang
 * @since 2016/12/20 上午11:09.
 */
public interface AsyncRunnable extends Runnable, ExecutorLoggerInter {

    void execute();

    @Override
    default void run() {
        Object rpcContext = EagleEye.currentRpcContext();
        EagleEye.setRpcContext(rpcContext);

        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            execute();
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
