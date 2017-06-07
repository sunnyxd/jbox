package com.alibaba.jbox.thread;

/**
 * 对Runnable的包装, 在Runnable进入task queue或task被reject时可以打印更详细的信息
 * 即使在executor.submit()时使用JDK原生的Runnable, 也会被封装成一个AsyncRunnable
 *
 * @author jifang
 * @since 2016/12/20 上午11:09.
 */
public abstract class AsyncRunnable implements Runnable, LoggerInter {

    protected abstract void asyncExecute();

    protected boolean isNeedCatchException() {
        return false;
    }

    public String getAsyncTaskInfo() {
        return generateDefaultTaskInfo(this.getClass());
    }

    @Override
    public void run() {
        if (!isNeedCatchException()) {
            asyncExecute();
        } else {
            try {
                asyncExecute();
            } catch (Throwable throwable) {
                LOGGER.error("task [{}] invoke error", getAsyncTaskInfo(), throwable);
                throw throwable;
            }
        }
    }

    private String generateDefaultTaskInfo(Class<? extends AsyncRunnable> clazz) {
        String name = clazz.getName();
        int i = name.lastIndexOf('.');
        if (i != -1) {
            name = name.substring(i + 1);
        }

        return name;
    }
}
