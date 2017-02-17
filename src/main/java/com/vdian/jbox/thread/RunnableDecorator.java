package com.vdian.jbox.thread;

/**
 * @author jifang
 * @since 2017/1/16 下午3:07.
 */
class RunnableDecorator extends AsyncRunnable {

    public Runnable runnable;

    public RunnableDecorator(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    protected void asyncExecute() {
        this.runnable.run();
    }
}
