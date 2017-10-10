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

    private static final Set<String> NEED_PROXY_METHODS = Sets.newConcurrentHashSet(Arrays.asList(
        "execute",
        "submit",
        "schedule",
        "scheduleAtFixedRate",
        "scheduleWithFixedDelay"
    ));

    private String group;

    private ExecutorService target;

    RunnableDecoratorInterceptor(String group, ExecutorService target) {
        this.group = group;
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        if (NEED_PROXY_METHODS.contains(methodName)) {
            Object rpcContext = EagleEye.getRpcContext();
            Object firstArg = args[0];
            FlightRecorder recorder = recorders.computeIfAbsent(group, (k) -> new FlightRecorder());
            Context context = new Context(Thread.currentThread(), group);
            if (firstArg instanceof AsyncRunnable) {
                args[0] = new AsyncRunnableDecorator(context, recorder, (AsyncRunnable)firstArg, rpcContext);
            } else if (firstArg instanceof AsyncCallable) {
                args[0] = new AsyncCallableDecorator(context, recorder, (AsyncCallable)firstArg, rpcContext);
            } else if (firstArg instanceof Runnable) {
                args[0] = new RunnableDecorator(context, recorder, (Runnable)firstArg, rpcContext);
            } else if (firstArg instanceof Callable) {
                args[0] = new CallableDecorator(context, recorder, (Callable)firstArg, rpcContext);
            }
        }

        return method.invoke(target, args);
    }
}

class RunnableDecorator implements AsyncRunnable {

    private Context context;

    private FlightRecorder recorder;

    private Runnable runnable;

    private Object rpcContext;

    RunnableDecorator(@NonNull Context context,
                      @NonNull FlightRecorder recorder,
                      @NonNull Runnable runnable,
                      Object rpcContext) {
        this.context = context;
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

            beforeExecute(context);
            runnable.run();
            afterExecute(context);

            // invoke rt
            recorder.getTotalRt().addAndGet((System.currentTimeMillis() - start));
            // success count
            recorder.getSuccess().incrementAndGet();
        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            // failure count
            recorder.getFailure().incrementAndGet();
            afterThrowing(e, context);
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

    @Override
    public void beforeExecute(final Context context) { }

    @Override
    public void afterExecute(final Context context) { }

    @Override
    public void afterThrowing(Throwable t, final Context context) { }
}

class CallableDecorator implements AsyncCallable {

    private Context context;

    private FlightRecorder recorder;

    private Callable callable;

    private Object rpcContext;

    CallableDecorator(@NonNull Context context,
                      @NonNull FlightRecorder recorder,
                      @NonNull Callable callable,
                      Object rpcContext) {
        this.context = context;
        this.recorder = recorder;
        this.callable = callable;
        this.rpcContext = rpcContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object call() throws Exception {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            long start = System.currentTimeMillis();

            beforeExecute(context);
            Object result = callable.call();
            afterExecute(result, context);

            // invoke rt
            recorder.getTotalRt().addAndGet((System.currentTimeMillis() - start));
            // success count
            recorder.getSuccess().incrementAndGet();

            return result;

        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            // failure count
            recorder.getFailure().incrementAndGet();
            afterThrowing(e, context);
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

    @Override
    public void beforeExecute(final Context context) { }

    @Override
    public void afterExecute(Object result, final Context context) { }

    @Override
    public void afterThrowing(Throwable t, final Context context) { }
}

class AsyncRunnableDecorator implements AsyncRunnable {

    private Context context;

    private FlightRecorder recorder;

    private AsyncRunnable asyncRunnable;

    private Object rpcContext;

    AsyncRunnableDecorator(@NonNull Context context,
                           @NonNull FlightRecorder recorder,
                           @NonNull AsyncRunnable asyncRunnable,
                           Object rpcContext) {
        this.context = context;
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

            beforeExecute(context);
            asyncRunnable.execute();
            afterExecute(context);

            // invoke rt
            recorder.getTotalRt().addAndGet((System.currentTimeMillis() - start));
            // success count
            recorder.getSuccess().incrementAndGet();
        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            // failure count
            recorder.getFailure().incrementAndGet();
            afterThrowing(e, context);
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

    @Override
    public void beforeExecute(final Context context) {
        asyncRunnable.beforeExecute(context);
    }

    @Override
    public void afterExecute(final Context context) {
        asyncRunnable.afterExecute(context);
    }

    @Override
    public void afterThrowing(Throwable t, final Context context) {
        asyncRunnable.afterThrowing(t, context);
    }
}

class AsyncCallableDecorator implements AsyncCallable {

    private Context context;

    private FlightRecorder recorder;

    private AsyncCallable asyncCallable;

    private Object rpcContext;

    AsyncCallableDecorator(@NonNull Context context,
                           @NonNull FlightRecorder recorder,
                           @NonNull AsyncCallable asyncCallable,
                           Object rpcContext) {
        this.context = context;
        this.recorder = recorder;
        this.asyncCallable = asyncCallable;
        this.rpcContext = rpcContext;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object call() throws Exception {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            long start = System.currentTimeMillis();

            beforeExecute(context);
            Object result = asyncCallable.execute();
            afterExecute(result, context);

            // invoke rt
            recorder.getTotalRt().addAndGet((System.currentTimeMillis() - start));
            // success count
            recorder.getSuccess().incrementAndGet();

            return result;
        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            // failure count
            recorder.getFailure().incrementAndGet();
            afterThrowing(e, context);
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

    @Override
    public void beforeExecute(final Context context) {
        asyncCallable.beforeExecute(context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterExecute(Object result, final Context context) {
        asyncCallable.afterExecute(result, context);
    }

    @Override
    public void afterThrowing(Throwable t, final Context context) {
        asyncCallable.afterThrowing(t, context);
    }
}