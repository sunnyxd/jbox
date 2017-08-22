package com.alibaba.jbox.executor;

/**
 * @author jifang
 * @since 2017/1/16 下午3:07.
 */
class RunnableDecorator implements AsyncRunnable {

    public Runnable runnable;

    public RunnableDecorator(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void execute() {
        runnable.run();
    }

    @Override
    public String taskInfo() {
        return runnable.getClass().getName();
    }
}
