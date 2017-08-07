package com.alibaba.jbox.executors;

import com.alibaba.jbox.scheduler.ScheduleTask;
import org.apache.commons.proxy.factory.cglib.CglibProxyFactory;

import javax.annotation.PreDestroy;
import java.util.concurrent.*;

/**
 * 线程池管理器:
 * <p/>
 * <b>最好注册为一个Spring Bean</b>
 * <p/>
 * 相比JDK原生{@code Executors}有以下优势:
 * <ol>
 * <li>每个分组{@code group}的线程默认是单例的, 防止出现在方法内部{@code new}线程池的错误写法
 * <li>线程以<b>group-n</b>的形式命名, 使在使用jstack/Apache Sorina等工具查看时更加清晰
 * <li>开放{@code newFixedMinMaxThreadPool()}方法, 提供比{@code Executors}更灵活, 但比{@code ThreadPoolExecutor}更方便的线程池配置方式
 * <li>提供{@code LoggedCallerRunsPolicy}的线程拒绝策略, 在RunnableQueue满时打印日志
 * <li>添加{@code ThreadPoolMonitor}监控: 将日志打印到<b>thread-pool-monitor</b> {@code Logger}下
 * <ul>
 * <li>a. 线程组信息
 * <li>b. 线程总数
 * <li>c. 活跃线程数
 * <li>d. 被阻塞的任务描述(在RunnableQueue内的任务的描述)
 * </ul>
 * <li>封装{@code Runnable}为{@code AsyncRunnable}, 为提交的任务增加描述信息
 * (即使提交的任务为原生的{@code Runnable}, 被代理的{@code ExecutorService}也会将其修改为{@code AsyncRunnable}:
 * 使用class name作为task info, 详见{@code AsyncRunnable}类实现)
 * <li>如果将{@code ThreadPoolManager}注册为Spring bean, 会在应用关闭时自动将忘记关闭的线程池自动关闭掉.
 * </ol>
 *
 * @author jifang
 * @since 2017/1/16 下午2:15.
 */
public class ThreadPoolManager implements LoggerInter {

    protected static final ConcurrentMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

    public static ExecutorService newFixedMinMaxThreadPool(String group, int minPoolSize, int maxPoolSize, int runnableQueueSize) {
        // 队满拒绝策略
        RejectedExecutionHandler rejectHandler = new LoggedCallerRunsPolicy(group);

        return newFixedMinMaxThreadPool(group, minPoolSize, maxPoolSize, runnableQueueSize, rejectHandler);
    }

    public static ExecutorService newFixedMinMaxThreadPool(String group, int minPoolSize, int maxPoolSize, int runnableQueueSize, RejectedExecutionHandler rejectHandler) {
        return executors.computeIfAbsent(group, (key) -> {
            // 任务缓存队列
            BlockingQueue<Runnable> runnableQueue = new ArrayBlockingQueue<>(runnableQueueSize);

            ThreadFactory threadFactory = new NamedThreadFactory(group);

            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(minPoolSize, maxPoolSize,
                    ScheduleTask.HALF_AN_HOUR_INTERVAL, TimeUnit.MILLISECONDS,
                    runnableQueue,
                    threadFactory,
                    rejectHandler);

            return createExecutorProxy(threadPoolExecutor);
        });
    }

    public static ExecutorService newFixedThreadPool(String group, int poolSize) {
        return executors.computeIfAbsent(group, (key) -> {

            ExecutorService executor = Executors.newFixedThreadPool(poolSize, new NamedThreadFactory(group));

            return createExecutorProxy(executor);
        });
    }

    public static ExecutorService newCachedThreadPool(String group) {
        return executors.computeIfAbsent(group, (key) -> {
            ExecutorService executor = Executors.newCachedThreadPool(new NamedThreadFactory(group));

            return createExecutorProxy(executor);
        });
    }

    public static ExecutorService newSingleThreadExecutor(String group) {
        return executors.computeIfAbsent(group, (key) -> {

            ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory(group));

            return createExecutorProxy(executor);
        });
    }

    public static ScheduledExecutorService newScheduledThreadPool(String group, int corePoolSize) {
        return (ScheduledExecutorService) executors.computeIfAbsent(group, (key) -> {
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(corePoolSize, new NamedThreadFactory(group));
            return createExecutorProxy(executor);
        });
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(String group) {
        return (ScheduledExecutorService) executors.computeIfAbsent(group, (key) -> {

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(group));

            return createExecutorProxy(executor);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends ExecutorService> T createExecutorProxy(T executor) {
        Object proxy = new CglibProxyFactory().createInterceptorProxy(
                executor,
                new RunnableDecoratorInterceptor(),
                new Class[]{executor.getClass()}
        );

        return (T) proxy;
    }

    @PreDestroy
    public void destroy() {
        executors.entrySet().stream()
                .filter(entry -> !entry.getValue().isShutdown())
                .forEach(entry -> {
                    entry.getValue().shutdown();
                    MONITOR_LOGGER.info("executor [{}] is shutdown", entry.getKey());
                    LOGGER.info("executor [{}] is shutdown", entry.getKey());
                });
    }
}
