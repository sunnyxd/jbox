package com.alibaba.jbox.executor;

/**
 * 类似{@code java.lang.Runnable}结构,
 * 在{@code Runnable}基础上添加了{@code taskInfo()}方法.
 * 1. 在task执行时设置rpc context信息;
 * 2. 在task执行时MDC塞入traceId信息;
 * 3. 当task进入RunnableQueue后/触发{@code java.util.concurrent.RejectedExecutionHandler}时打印更详细的的信息.
 *
 * (使用了{@code ExecutorManager}后, 即使'submit()'使用的是原生Runnable, 也会被封装成一个AsyncRunnable)
 *
 * @author jifang
 * @since 2016/12/20 上午11:09.
 */
@FunctionalInterface
public interface AsyncRunnable extends java.lang.Runnable, ExecutorLoggerInter {

    /**
     * implements like {@code Runnable.run()}
     */
    void execute();

    /**
     * 对要执行任务详细的描述.
     *
     * @return default task class name.
     */
    default String taskInfo() {
        return this.getClass().getName();
    }

    @Override
    default void run() {
        execute();
    }
}
