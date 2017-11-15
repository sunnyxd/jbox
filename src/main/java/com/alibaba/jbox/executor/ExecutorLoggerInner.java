package com.alibaba.jbox.executor;

import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * @author jifang
 * @since 2017/1/16 下午5:39.
 */
public interface ExecutorLoggerInner {

    String TRACE_ID = "traceId";

    Logger logger = LoggerFactory.getLogger("com.alibaba.jbox.executor");

    Logger monitor = LoggerFactory.getLogger("executor-monitor");

    default String generatePolicyLoggerContent(String group, Object policy, BlockingQueue<?> queue,
                                               String taskInfo, int taskHash) {
        return MessageFormatter.arrayFormat(
            "executor group:[{}] triggered policy:[{}], RunQ remain:[{}], task: '{}', obj: {}",
            new Object[] {group, policy.getClass().getSimpleName(), queue.remainingCapacity(), taskInfo, taskHash})
            .getMessage();
    }
}
