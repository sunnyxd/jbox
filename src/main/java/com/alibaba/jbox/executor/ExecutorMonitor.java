package com.alibaba.jbox.executor;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

import com.alibaba.jbox.scheduler.ScheduleTask;
import com.alibaba.jbox.scheduler.TaskScheduler;
import com.alibaba.jbox.spring.AbstractApplicationContextAware;
import com.alibaba.jbox.stream.StreamForker;
import com.alibaba.jbox.utils.JboxUtils;
import com.alibaba.jbox.utils.ProxyUtil;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.util.ReflectionUtils;

import static com.alibaba.jbox.utils.JboxUtils.getUsableBeanName;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.4
 * @since 2017/8/22 15:32:00.
 */
public class ExecutorMonitor extends AbstractApplicationContextAware
        implements ScheduleTask, ExecutorLoggerInter, BeanDefinitionRegistryPostProcessor {

    private static final String ASYNC_KEY = "async";

    private static final String FUTURE_KEY = "future";

    private static final String CALLABLE_KEY = "callable";

    private long period = _1M_INTERVAL;

    @Override
    public void invoke() throws Exception {

        StringBuilder logBuilder = new StringBuilder(1000);
        logBuilder.append("executors group [")
                .append(ExecutorManager.executors.size())
                .append("]:\n");

        ExecutorManager.executors.forEach((group, executorProxy) -> {
            ThreadPoolExecutor executor = getThreadPoolExecutor(executorProxy);

            BlockingQueue<Runnable> queue = executor.getQueue();
            logBuilder.append(String.format("\tgroup:[%s], pool:[%s], active:[%d], core pool:[%d], max pool:[%d], task in queue:[%d], remain:[%d]\n",
                    group,
                    executor.getPoolSize(),
                    executor.getActiveCount(),
                    executor.getCorePoolSize(),
                    executor.getMaximumPoolSize(),
                    queue.size(),
                    queue.remainingCapacity()));

            StreamForker<Runnable> forker = new StreamForker<>(queue.stream())
                    .fork(ASYNC_KEY, stream -> stream
                            .filter(runnable -> runnable instanceof AsyncRunnable)
                            .collect(new Collector()))
                    .fork(FUTURE_KEY, stream -> stream
                            .filter(runnable -> runnable instanceof FutureTask)
                            .map(this::getFutureTaskInnerAsyncObject)
                            .collect(new Collector()))
                    .fork(CALLABLE_KEY, stream -> stream
                            .filter(callable -> callable instanceof AsyncCallable)
                            .collect(new Collector()));

            StreamForker.Results results = forker.getResults();
            StringBuilder asyncLogBuilder = results.get(ASYNC_KEY);
            StringBuilder futureLogBuilder = results.get(FUTURE_KEY);
            StringBuilder callableLogBuilder = results.get(CALLABLE_KEY);

            logBuilder
                    .append(asyncLogBuilder)
                    .append(futureLogBuilder)
                    .append(callableLogBuilder);
        });

        monitor.info(logBuilder.toString());
    }

    /**
     * @param executorProxy
     * @return
     * @since 1.1
     */
    private ThreadPoolExecutor getThreadPoolExecutor(ExecutorService executorProxy) {
        ThreadPoolExecutor executor = null;

        if (executorProxy instanceof ThreadPoolExecutor) {
            executor = (ThreadPoolExecutor) executorProxy;
        } else if (Proxy.isProxyClass(executorProxy.getClass())) {
            Object target = ProxyUtil.getProxyTarget(executorProxy);
            if (target instanceof ThreadPoolExecutor) {
                executor = (ThreadPoolExecutor) target;
            } else {
                executor = (ThreadPoolExecutor) JboxUtils.getFieldValue(target, "e");
            }
        }

        return executor;
    }

    /**
     * @since 1.2
     */
    private class Collector implements java.util.stream.Collector<Object, StringBuilder, StringBuilder> {

        @Override
        public Supplier<StringBuilder> supplier() {
            return StringBuilder::new;
        }

        @Override
        public BiConsumer<StringBuilder, Object> accumulator() {
            return (stringBuilder, object) -> {
                Method taskInfoMethod = ReflectionUtils.findMethod(object.getClass(), "taskInfo");
                ReflectionUtils.makeAccessible(taskInfoMethod);
                stringBuilder.append("\t -> ")
                        .append("task: ")
                        .append(ReflectionUtils.invokeMethod(taskInfoMethod, object))
                        .append(", obj: ")
                        .append(Objects.hashCode(object))
                        .append("\n");
            };
        }

        @Override
        public BinaryOperator<StringBuilder> combiner() {
            return StringBuilder::append;
        }

        @Override
        public Function<StringBuilder, StringBuilder> finisher() {
            return Function.identity();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return EnumSet.of(Characteristics.CONCURRENT, Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
        }
    }

    private Object getFutureTaskInnerAsyncObject(Object futureTask) {
        Object callable = JboxUtils.getFieldValue(futureTask, "callable");
        if (callable instanceof AsyncCallable) {
            return callable;
        } else {
            return JboxUtils.getFieldValue(callable, "task");
        }
    }

    @Override
    public long period() {
        return period;
    }

    /**
     * @param registry
     * @throws BeansException
     * @since 1.4
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        Map<String, TaskScheduler> beans = applicationContext.getBeansOfType(TaskScheduler.class);
        if (beans == null || beans.isEmpty()) {
            RootBeanDefinition taskScheduler = new RootBeanDefinition(TaskScheduler.class);
            taskScheduler.setInitMethodName("start");
            taskScheduler.setDestroyMethodName("shutdown");
            taskScheduler.setScope(SCOPE_SINGLETON);
            taskScheduler.getConstructorArgumentValues().addIndexedArgumentValue(0, true);
            registry.registerBeanDefinition(getUsableBeanName("com.alibaba.jbox.scheduler.TaskScheduler", registry), taskScheduler);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        beanFactory.getBean(TaskScheduler.class).register(this);
    }

    public void setPeriod(long period) {
        this.period = period;
    }
}
