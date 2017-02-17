package com.vdian.jbox.thread;

import com.vdian.jbox.scheduler.ScheduleTask;
import org.apache.commons.proxy.ProxyFactory;
import org.apache.commons.proxy.factory.cglib.CglibProxyFactory;

import javax.annotation.PreDestroy;
import java.util.Map;
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
 * <li>开放{@code fixedMinMaxThreadPool()}方法, 提供比{@code Executors}更灵活, 但比{@code ThreadPoolExecutor}更方便的线程池配置方式
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

    static final ConcurrentMap<String, ExecutorService> EXECUTOR_MAP = new ConcurrentHashMap<>();

    public static ExecutorService fixedMinMaxThreadPool(String group, int minPoolSize, int maxPoolSize, int runQueueSize) {
        // 队满拒绝策略
        RejectedExecutionHandler rejectHandler = new LoggedCallerRunsPolicy(group);

        return fixedMinMaxThreadPool(group, minPoolSize, maxPoolSize, runQueueSize, rejectHandler);
    }

    public static ExecutorService fixedMinMaxThreadPool(String group, int minPoolSize, int maxPoolSize, int runQueueSize, RejectedExecutionHandler rejectHandler) {
        ExecutorService executor = EXECUTOR_MAP.get(group);
        // single check idiom - can cause repeated initialization
        if (executor == null) {
            // 任务缓存队列
            BlockingQueue<Runnable> runnableQueue = new ArrayBlockingQueue<>(runQueueSize);

            ThreadFactory threadFactory = new NamedThreadFactory(group);

            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(minPoolSize, maxPoolSize,
                    ScheduleTask.HALF_AN_HOUR_INTERVAL, TimeUnit.MILLISECONDS,
                    runnableQueue,
                    threadFactory,
                    rejectHandler);

            executor = createProxyExecutor(threadPoolExecutor);

            EXECUTOR_MAP.put(group, executor);
        }

        return executor;
    }

    public static ExecutorService fixedThreadPool(String group, int poolSize) {

        ExecutorService executor = EXECUTOR_MAP.get(group);
        if (executor == null) {
            ThreadFactory threadFactory = new NamedThreadFactory(group);

            ExecutorService fixedThreadPool = Executors.newFixedThreadPool(poolSize, threadFactory);

            executor = createProxyExecutor(fixedThreadPool);
            EXECUTOR_MAP.put(group, executor);
        }

        return executor;
    }

    public static ExecutorService cachedThreadPool(String group) {

        ExecutorService executor = EXECUTOR_MAP.get(group);
        if (executor == null) {
            ThreadFactory threadFactory = new NamedThreadFactory(group);
            ExecutorService cachedThreadPool = Executors.newCachedThreadPool(threadFactory);

            executor = createProxyExecutor(cachedThreadPool);
            EXECUTOR_MAP.put(group, executor);
        }

        return executor;
    }

    public static ExecutorService singleThreadExecutor(String group) {
        ExecutorService executor = EXECUTOR_MAP.get(group);
        if (executor == null) {
            ThreadFactory threadFactory = new NamedThreadFactory(group);
            ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor(threadFactory);

            executor = createProxyExecutor(singleThreadExecutor);
            EXECUTOR_MAP.put(group, executor);
        }

        return executor;
    }

    private static ExecutorService createProxyExecutor(ExecutorService executor) {
        ProxyFactory factory = new CglibProxyFactory();
        Object proxy = factory.createInterceptorProxy(
                executor,
                new RunnableDecoratorInterceptor(),
                new Class[]{ExecutorService.class}
        );

        return (ExecutorService) proxy;
    }

    @PreDestroy
    public void destroy() {
        Map<String, ExecutorService> executors = EXECUTOR_MAP;
        for (Map.Entry<String, ExecutorService> entry : executors.entrySet()) {
            ExecutorService executor = entry.getValue();

            if (!executor.isShutdown()) {
                executor.shutdown();

                String msg = String.format("thread pool [%s] is shutdown", entry.getKey());
                MONITOR_LOGGER.info(msg);
                LOGGER.info(msg);
            }
        }
    }
}
