package com.alibaba.jbox.trace;

import com.alibaba.jbox.utils.JboxUtils;
import com.google.common.base.Strings;
import com.taobao.eagleeye.EagleEye;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author jifang
 * @since 2016/11/25 上午11:53.
 */
@Aspect
public class TraceAspect {

    /**
     * add logback.xml or log4j.xml {@code %X{traceId} }
     * in {@code <pattern></pattern>} config
     */
    private static final String TRACE_ID = "traceId";

    private static final Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    private static final Logger traceLogger = LoggerFactory.getLogger("com.alibaba.jbox.trace");

    // <package.class.method, logger>
    private static final ConcurrentMap<String, Logger> bizLoggers = new ConcurrentHashMap<>();

    @Around("@annotation(com.alibaba.jbox.trace.Trace)")
    public Object invoke(final ProceedingJoinPoint joinPoint) throws Throwable {
        MDC.put(TRACE_ID, EagleEye.getTraceId());
        try {
            long start = System.currentTimeMillis();

            Object result = joinPoint.proceed();

            Method method = JboxUtils.getRealMethod(joinPoint);
            Trace trace = method.getAnnotation(Trace.class);
            if (trace != null) {
                long costTime = System.currentTimeMillis() - start;

                // log over time
                if (isNeedLogger(trace, costTime)) {
                    String logContent = buildLogContent(method, costTime, trace, joinPoint.getArgs());

                    logBiz(logContent, method, joinPoint, trace);
                    logTrace(logContent);
                }
            }

            return result;
        } catch (Throwable e) {
            rootLogger.error("", e);
            throw e;
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    private boolean isNeedLogger(Trace trace, long costTime) {
        return trace.value() && costTime > trace.threshold();
    }

    private String buildLogContent(Method method, long costTime, Trace trace, Object[] args) {
        StringBuilder logBuilder = new StringBuilder(120);
        logBuilder
                .append("method: [")
                .append(method.getDeclaringClass().getName())
                .append('.')
                .append(method.getName())
                .append("] invoke total cost [")
                .append(costTime)
                .append("]ms");

        if (trace.param()) {
            logBuilder.append(", params:")
                    .append(Arrays.toString(args))
                    .append(".");
        } else {
            logBuilder.append('.');
        }

        return logBuilder.toString();
    }

    private void logBiz(String logContent, Method method, Object target, Trace trace) {
        Class<?> clazz = method.getDeclaringClass();
        String methodName = MessageFormat.format("{0}.{1}", clazz.getName(), method.getName());
        Logger bizLogger = bizLoggers.computeIfAbsent(methodName, key -> {
            try {
                if (Strings.isNullOrEmpty(trace.logger())) {
                    return getDefaultBizLogger(clazz, target);
                } else {
                    return getNamedBizLogger(trace.logger(), clazz, target);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new TraceException(e);
            }
        });

        bizLogger.warn(logContent);
    }

    private void logTrace(String logContent) {
        traceLogger.warn(logContent);
    }

    private Logger getDefaultBizLogger(Class<?> clazz, Object target) throws IllegalAccessException {
        Logger bizLogger = null;
        for (Field field : clazz.getDeclaredFields()) {
            if (Logger.class.isAssignableFrom(field.getType())) {
                if (bizLogger == null) {
                    field.setAccessible(true);
                    bizLogger = (Logger) field.get(target);
                } else {
                    throw new TraceException("duplicated field's type is 'org.slf4j.Logger', please specify the used Logger name in @Trace.name()");
                }
            }
        }

        return bizLogger;
    }

    private Logger getNamedBizLogger(String loggerName, Class<?> clazz, Object target) throws NoSuchFieldException, IllegalAccessException {
        Field loggerField = clazz.getDeclaredField(loggerName);
        loggerField.setAccessible(true);
        return (Logger) loggerField.get(target);
    }
}
