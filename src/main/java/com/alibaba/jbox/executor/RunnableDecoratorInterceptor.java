package com.alibaba.jbox.executor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import com.alibaba.jbox.executor.ExecutorManager.FlightRecorder;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.taobao.eagleeye.EagleEye;
import lombok.NonNull;
import org.slf4j.MDC;

import static com.alibaba.jbox.executor.ExecutorManager.recorders;

/**
 * @author jifang@alibaba-inc.com
 * @version 1.1
 * @since 2017/1/16 下午3:42.
 */
class RunnableDecoratorInterceptor implements InvocationHandler {

    private String group;

    private ExecutorService target;

    RunnableDecoratorInterceptor(String group, ExecutorService target) {
        this.group = group;
        this.target = target;
    }

    private static final Set<String> NEED_PROXY_METHODS = Sets.newConcurrentHashSet(Arrays.asList(
        "execute",
        "submit",
        "schedule",
        "scheduleAtFixedRate",
        "scheduleWithFixedDelay"
    ));

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        if (NEED_PROXY_METHODS.contains(methodName)) {
            Object rpcContext = EagleEye.getRpcContext();
            Object firstArg = args[0];
            FlightRecorder recorder = recorders.computeIfAbsent(group, (k) -> new FlightRecorder());

            if (firstArg instanceof AsyncRunnable) {
                args[0] = new AsyncRunnableDecorator(recorder, (AsyncRunnable)firstArg, rpcContext);
            } else if (firstArg instanceof AsyncCallable) {
                args[0] = new AsyncCallableDecorator(recorder, (AsyncCallable)firstArg, rpcContext);
            } else if (firstArg instanceof Runnable) {
                args[0] = new RunnableDecorator(recorder, (Runnable)firstArg, rpcContext);
            } else if (firstArg instanceof Callable) {
                args[0] = new CallableDecorator(recorder, (Callable)firstArg, rpcContext);
            }
        }

        return method.invoke(target, args);
    }
}

class RunnableDecorator implements AsyncRunnable {

    private FlightRecorder recorder;

    private Runnable runnable;

    private Object rpcContext;

    RunnableDecorator(FlightRecorder recorder, @NonNull Runnable runnable, Object rpcContext) {
        this.recorder = recorder;
        this.runnable = runnable;
        this.rpcContext = rpcContext;
    }

    @Override
    public void run() {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            long start = System.currentTimeMillis();

            runnable.run();

            // invoke rt
            recorder.getTotalRt().addAndGet((System.currentTimeMillis() - start));
            // success count
            recorder.getSuccess().incrementAndGet();
        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            // failure count
            recorder.getFailure().incrementAndGet();
            throw e;
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
            EagleEye.clearRpcContext();
        }
    }

    @Override
    public String taskInfo() {
        return runnable.getClass().getName();
    }

    @Override
    public void execute() { }
}

class CallableDecorator implements AsyncCallable {

    private FlightRecorder recorder;

    private Callable callable;

    private Object rpcContext;

    CallableDecorator(FlightRecorder recorder, @NonNull Callable callable, Object rpcContext) {
        this.recorder = recorder;
        this.callable = callable;
        this.rpcContext = rpcContext;
    }

    @Override
    public Object call() throws Exception {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            long start = System.currentTimeMillis();

            Object result = callable.call();

            // invoke rt
            recorder.getTotalRt().addAndGet((System.currentTimeMillis() - start));
            // success count
            recorder.getSuccess().incrementAndGet();

            return result;

        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            // failure count
            recorder.getFailure().incrementAndGet();
            throw e;
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
            EagleEye.clearRpcContext();
        }
    }

    @Override
    public String taskInfo() {
        return callable.getClass().getName();
    }

    @Override
    public Object execute() throws Exception {
        return null;
    }
}

class AsyncRunnableDecorator implements AsyncRunnable {

    private FlightRecorder recorder;

    private AsyncRunnable asyncRunnable;

    private Object rpcContext;

    AsyncRunnableDecorator(FlightRecorder recorder, @NonNull AsyncRunnable asyncRunnable,
                           Object rpcContext) {
        this.recorder = recorder;
        this.asyncRunnable = asyncRunnable;
        this.rpcContext = rpcContext;
    }

    @Override
    public void run() {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            long start = System.currentTimeMillis();
            asyncRunnable.execute();
            // invoke rt
            recorder.getTotalRt().addAndGet((System.currentTimeMillis() - start));
            // success count
            recorder.getSuccess().incrementAndGet();
        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            // failure count
            recorder.getFailure().incrementAndGet();
            throw e;
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
            EagleEye.clearRpcContext();
        }
    }

    @Override
    public String taskInfo() {
        return asyncRunnable.taskInfo();
    }

    @Override
    public void execute() { }
}

class AsyncCallableDecorator implements AsyncCallable {

    private FlightRecorder recorder;

    private AsyncCallable asyncCallable;

    private Object rpcContext;

    AsyncCallableDecorator(FlightRecorder recorder, @NonNull AsyncCallable asyncCallable,
                           Object rpcContext) {
        this.recorder = recorder;
        this.asyncCallable = asyncCallable;
        this.rpcContext = rpcContext;
    }

    @Override
    public Object call() throws Exception {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            long start = System.currentTimeMillis();

            Object result = asyncCallable.execute();

            // invoke rt
            recorder.getTotalRt().addAndGet((System.currentTimeMillis() - start));
            // success count
            recorder.getSuccess().incrementAndGet();

            return result;
        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            // failure count
            recorder.getFailure().incrementAndGet();
            throw e;
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
            EagleEye.clearRpcContext();
        }
    }

    @Override
    public String taskInfo() {
        return asyncCallable.taskInfo();
    }

    @Override
    public Object execute() throws Exception {
        return null;
    }
}