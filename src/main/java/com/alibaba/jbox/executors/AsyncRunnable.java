package com.alibaba.jbox.executors;

/**
 * 对Runnable的包装, 在Runnable进入task queue或task被reject时可以打印更详细的信息
 * 即使在executor.submit()时使用JDK原生的Runnable, 也会被封装成一个AsyncRunnable
 *
 * @author jifang
 * @since 2016/12/20 上午11:09.
 */
public interface AsyncRunnable extends Runnable, LoggerInter {

    void execute();

    @Override
    default void run() {
        execute();
    }

    default String taskInfo() {
        return this.getClass().getName();
    }
}
