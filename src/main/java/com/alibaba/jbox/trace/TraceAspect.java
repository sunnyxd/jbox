package com.alibaba.jbox.trace;


import com.alibaba.fastjson.JSONObject;
import com.alibaba.jbox.utils.JboxUtils;
import com.google.common.base.Strings;
import com.taobao.csp.sentinel.Entry;
import com.taobao.csp.sentinel.SphU;
import com.taobao.csp.sentinel.slots.block.BlockException;
import com.taobao.eagleeye.EagleEye;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.4
 *          - 1.0: append 'traceId' to logger;
 *          - 1.1: append 'method invoke cost time & param' to biz logger;
 *          - 1.2: validate method param {@code com.alibaba.jbox.annotation.NotNull}, {@code com.alibaba.jbox.annotation.NotEmpty};
 *          - 1.3: replace validator instance to hibernate-validator;
 *          - 1.4: add sentinel on invoked service interface.
 * @since 2016/11/25 上午11:53.
 */
@Aspect
public class TraceAspect {

    /**
     * add logback.xml or log4j.xml {@code %X{traceId}} in {@code <pattern/>} config.
     */
    private static final String TRACE_ID = "traceId";

    private static final Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    private static final Logger traceLogger = LoggerFactory.getLogger("com.alibaba.jbox.trace");

    private static final ConcurrentMap<String, Logger> bizLoggers = new ConcurrentHashMap<>();

    /**
     * determine 'validate arguments' use or not.
     */
    private volatile boolean validator = false;

    /**
     * determine 'log method invoke cost time' use or not.
     */
    private volatile boolean elapsed = false;

    /**
     * determine the 'log method invoke cost time' append method param or not.
     */
    private volatile boolean param = true;

    /**
     * determine use sentinel for 'rate limit' ...
     */
    private volatile boolean sentinel = false;

    public TraceAspect() {
        this(false, false, false, false);
    }

    public TraceAspect(boolean validator, boolean elapsed, boolean param, boolean sentinel) {
        this.validator = validator;
        this.elapsed = elapsed;
        this.param = param;
        this.sentinel = sentinel;
    }

    @Around("@annotation(com.alibaba.jbox.trace.Trace)")
    public Object invoke(final ProceedingJoinPoint joinPoint) throws Throwable {

        /*
         * @since 1.0: put traceId
         */
        MDC.put(TRACE_ID, EagleEye.getTraceId());
        try {
            long start = System.currentTimeMillis();
            Method method = JboxUtils.getRealMethod(joinPoint);
            Object[] args = joinPoint.getArgs();

            /*
             * @since 1.2 validate arguments
             */
            if (validator) {
                validateArguments(joinPoint.getTarget(), method, args);
            }

            /*
             * @since 1.4 sentinel
             */
            if (sentinel) {
                entry(method);
            }

            Object result = joinPoint.proceed(args);

            /*
             * @since 1.1 logger invoke elapsed time & parameters
             */
            Trace trace;
            if (elapsed && (trace = method.getAnnotation(Trace.class)) != null) {
                long costTime = System.currentTimeMillis() - start;
                if (isNeedLogger(trace, costTime)) {
                    String logContent = buildLogContent(method, costTime, trace, args);

                    logBiz(logContent, method, joinPoint, trace);
                    logTrace(logContent);
                }
            }

            return result;
        } catch (Throwable e) {
            rootLogger.error("method: [{}.{}] invoke failed",
                    joinPoint.getTarget().getClass().getName(),
                    ((MethodSignature) joinPoint.getSignature()).getMethod().getName(),
                    e);
            throw e;
        } finally {
            MDC.remove(TRACE_ID);
        }
    }


    /*** ******************************* ***/
    /***  append cost logger @since 1.1  ***/
    /*** ******************************* ***/

    private boolean isNeedLogger(Trace trace, long costTime) {
        return costTime > trace.threshold();
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

        if (param) {
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
                if (Strings.isNullOrEmpty(trace.value())) {
                    return getDefaultBizLogger(clazz, target);
                } else {
                    return getNamedBizLogger(trace.value(), clazz, target);
                }
            } catch (IllegalAccessException e) {
                throw new TraceException(e);
            }
        });

        if (bizLogger != null) {
            bizLogger.warn(logContent);
        }
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

    private Logger getNamedBizLogger(String loggerName, Class<?> clazz, Object target) {
        try {
            Field loggerField = clazz.getDeclaredField(loggerName);
            loggerField.setAccessible(true);
            return (Logger) loggerField.get(target);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new TraceException("not such 'org.slf4j.Logger' instance named [" + loggerName + "], in class [" + clazz.getName() + "]");
        }
    }

    /*** ******************************************* ***/
    /***  validator arguments with Validator @since 1.3  ***/
    /*** ******************************************* ***/
    private static class InnerValidator {
        private static final ExecutableValidator validator;

        static {
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            validator = factory.getValidator().forExecutables();
        }
    }

    private void validateArguments(Object target, Method method, Object[] args) {
        Set<ConstraintViolation<Object>> violationSet = InnerValidator.validator.validateParameters(target, method, args);
        if (violationSet != null && !violationSet.isEmpty()) {
            StringBuilder msgBuilder = new StringBuilder(128);
            for (ConstraintViolation violation : violationSet) {
                msgBuilder
                        .append("path: ")
                        .append(violation.getPropertyPath())
                        .append(", err msg:")
                        .append(violation.getMessage())
                        .append("\n");
            }
            msgBuilder.append("original parameters: ")
                    .append(JSONObject.toJSONString(args))
                    .append("\n");

            throw new ValidationException(msgBuilder.toString());
        }
    }

    /*** ************************* ***/
    /***  add sentinel @since 1.4  ***/
    /*** ************************* ***/
    private void entry(Method method) throws BlockException {
        Entry entry = null;
        try {
            if (!Modifier.isPrivate(method.getModifiers()) && !Modifier.isProtected(method.getModifiers())) {
                entry = SphU.entry(method);
            }
        } catch (BlockException e) {
            rootLogger.warn("method: [{}.{}] invoke was blocked by sentinel.",
                    method.getDeclaringClass().getName(),
                    method.getName());
            throw e;
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    public boolean isValidator() {
        return validator;
    }

    public void setValidator(boolean validator) {
        this.validator = validator;
    }

    public boolean isElapsed() {
        return elapsed;
    }

    public void setElapsed(boolean elapsed) {
        this.elapsed = elapsed;
    }

    public boolean isParam() {
        return param;
    }

    public void setParam(boolean param) {
        this.param = param;
    }

    public boolean isSentinel() {
        return sentinel;
    }

    public void setSentinel(boolean sentinel) {
        this.sentinel = sentinel;
    }
}
