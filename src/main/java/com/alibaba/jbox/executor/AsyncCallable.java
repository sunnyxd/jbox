package com.alibaba.jbox.executor;

/**
 * 类似{@code java.util.concurrent.Callable}结构,
 * 在{@code Callable}基础上添加了{@code taskInfo()}方法.
 * 1. 在task执行时设置rpc context信息;
 * 2. 在task执行时MDC塞入traceId信息;
 * 3. 当task进入RunnableQueue后/触发{@code java.util.concurrent.RejectedExecutionHandler}时打印更详细的的信息.
 *
 * (使用了{@code ExecutorManager}后, 即使'submit()'使用的是原生Callable, 也会被封装成一个AsyncCallable)
 *
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/8/24 10:20:00.
 */
@FunctionalInterface
public interface AsyncCallable<V> extends java.util.concurrent.Callable<V>, ExecutorLoggerInter {

    /**
     * implements like {@code Callable.call()}
     *
     * @return
     * @throws Exception
     */
    V execute() throws Exception;

    /**
     * 对要执行任务详细的描述.
     *
     * @return default task class name.
     */
    default String taskInfo() {
        return this.getClass().getName();
    }

    @Override
    default V call() throws Exception {
        return execute();
    }
}
