package com.alibaba.jbox.trace;

import com.alibaba.jbox.utils.JboxUtils;
import com.taobao.eagleeye.EagleEye;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author jifang
 * @since 2016/11/25 上午11:53.
 */
@Aspect
public class TraceAspect {

    private static boolean hasMdc = false;

    static {
        try {
            Class.forName("org.slf4j.MDC");
            hasMdc = true;
        } catch (Exception e) {
            System.err.println("slf4j not exits");
        }
    }

    /**
     * add logback.xml or log4j.xml {@code %X{traceId} }
     * in {@code <pattern></pattern>} config
     */
    private static final String TRACE_ID = "traceId";

    private static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.jbox.trace");

    private static final Logger ROOT_LOGGER = LoggerFactory.getLogger(TraceAspect.class);

    @Around("@annotation(com.alibaba.jbox.trace.Trace)")
    public Object invoke(final ProceedingJoinPoint joinPoint) throws Throwable {
        if (hasMdc) {
            org.slf4j.MDC.put(TRACE_ID, EagleEye.getTraceId());
        }
        long start = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            ROOT_LOGGER.error("", e);
            throw e;
        } finally {
            if (hasMdc) {
                org.slf4j.MDC.remove(TRACE_ID);
            }
        }

        Method method = JboxUtils.getRealMethod(joinPoint);
        Trace trace = method.getAnnotation(Trace.class);
        long cost = System.currentTimeMillis() - start;
        if (cost >= trace.value()) { // record
            onTimeOut(cost, joinPoint.getTarget(), method, joinPoint.getArgs());
        }

        return result;
    }

    protected void onTimeOut(long constTime, Object target, Method method, Object[] args) {
        String clazzName = method.getDeclaringClass().getName();
        String methodName = method.getName();
        String message = String.format("method: [%s.%s] invoke total cost [%s]ms, params=%s",
                clazzName,
                methodName,
                constTime,
                Arrays.toString(args));

        LOGGER.warn(message);
    }
}
