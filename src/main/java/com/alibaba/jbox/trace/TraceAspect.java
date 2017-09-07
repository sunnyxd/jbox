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

import static com.alibaba.jbox.utils.JboxUtils.getSimplifiedMethodName;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.6
 *          - 1.0: append 'traceId' to logger;
 *          - 1.1: append 'method invoke cost time & param' to biz logger;
 *          - 1.2: validate method param {@code com.alibaba.jbox.annotation.NotNull}, {@code com.alibaba.jbox.annotation.NotEmpty};
 *          - 1.3: replace validator instance to hibernate-validator;
 *          - 1.4: add sentinel on invoked service interface;
 *          - 1.5: append method invoke result on logger content;
 *          - 1.6: support use TraceAspect not with {@code com.alibaba.jbox.trace.TraceAspect} annotation.
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
     * used when method's implementation class has non default logger instance.
     */
    private Logger defaultBizLogger;

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
     * determine the 'log method invoke cost time' append method invoke result or not.
     */
    private volatile boolean result = true;

    /**
     * determine use sentinel for 'rate limit' ...
     */
    private volatile boolean sentinel = false;

    /**
     * use for replace @Trace when not use Trace annotation.
     * methodKey:{className:MethodName} -> TraceConfig
     */
    private ConcurrentMap<String, TraceConfig> traceConfigs = new ConcurrentHashMap<>();

    @Around("@annotation(com.alibaba.jbox.trace.Trace)")
    public Object invoke(final ProceedingJoinPoint joinPoint) throws Throwable {

        /*
         * @since 1.0: put traceId
         */
        MDC.put(TRACE_ID, EagleEye.getTraceId());
        try {
            long start = System.currentTimeMillis();
            Method abstractMethod = JboxUtils.getAbstractMethod(joinPoint);
            Method implMethod = JboxUtils.getImplMethod(joinPoint);
            String methodKey = String.format("%s:%s", abstractMethod.getDeclaringClass().getName(), abstractMethod.getName());

            Object[] args = joinPoint.getArgs();

            /*
             * @since 1.2 validate arguments
             */
            if (validator) {
                validateArguments(joinPoint.getTarget(), implMethod, args);
            }

            /*
             * @since 1.4 sentinel
             */
            if (sentinel) {
                entry(abstractMethod);
            }

            Object result = joinPoint.proceed(args);

            /*
             * @since 1.1 logger invoke elapsed time & parameters
             */
            Trace trace = implMethod.getAnnotation(Trace.class);
            Object[] config = getConfig(methodKey, trace);
            if (elapsed) {
                long costTime = System.currentTimeMillis() - start;
                if (isNeedLogger((Long) config[0], costTime)) {
                    String logContent = buildLogContent(abstractMethod, costTime, args, result);

                    logBiz(logContent, implMethod, joinPoint, (String) config[1]);
                    logTrace(logContent);
                }
            }

            return result;
        } catch (Throwable e) {
            rootLogger.error("method: [{}] invoke failed",
                    getSimplifiedMethodName(JboxUtils.getAbstractMethod(joinPoint)),
                    e);
            throw e;
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    // @since 1.6
    private Object[] getConfig(String methodKey, Trace trace) {
        long threshold;
        String loggerName;
        if (trace == null) {
            TraceConfig config = this.traceConfigs.computeIfAbsent(methodKey, key -> new TraceConfig());
            threshold = config.getThreshold();
            loggerName = config.getLogger();
        } else {
            threshold = trace.threshold();
            loggerName = trace.value();
        }

        return new Object[]{threshold, loggerName};
    }

    /*** ******************************* ***/
    /***  append cost logger @since 1.1  ***/
    /*** ******************************* ***/

    private boolean isNeedLogger(long threshold, long costTime) {
        return costTime > threshold;
    }

    private String buildLogContent(Method abstractMethod, long costTime, Object[] args, Object resultObj) {
        StringBuilder logBuilder = new StringBuilder(120);
        logBuilder
                .append("method: [")
                .append(getSimplifiedMethodName(abstractMethod))
                .append("] invoke total cost [")
                .append(costTime)
                .append("]ms");

        if (param) {
            logBuilder.append(", params:")
                    .append(Arrays.toString(args));
        }

        // @since 1.5
        if (result) {
            logBuilder.append(", result: ")
                    .append(JSONObject.toJSONString(resultObj))
                    .append(".");
        } else {
            logBuilder.append('.');
        }

        return logBuilder.toString();
    }

    private void logBiz(String logContent, Method method, Object target, String loggerName) {
        Class<?> clazz = method.getDeclaringClass();
        String methodName = MessageFormat.format("{0}.{1}", clazz.getName(), method.getName());
        Logger bizLogger = bizLoggers.computeIfAbsent(methodName, key -> {
            try {
                if (Strings.isNullOrEmpty(loggerName)) {
                    return defaultBizLogger == null ? getDefaultBizLogger(clazz, target) : defaultBizLogger;
                } else {
                    return getNamedBizLogger(loggerName, clazz, target);
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
        traceLogger.info(logContent);
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
            msgBuilder.append("your request params: ")
                    .append(JSONObject.toJSONString(args))
                    .append("\n");

            throw new ValidationException(msgBuilder.toString());
        }
    }

    /*** ************************* ***/
    /***  add sentinel @since 1.4  ***/
    /*** ************************* ***/
    private void entry(Method abstractMethod) throws TraceException {
        Entry entry = null;
        try {
            if (!Modifier.isPrivate(abstractMethod.getModifiers()) && !Modifier.isProtected(abstractMethod.getModifiers())) {
                entry = SphU.entry(abstractMethod);
            }
        } catch (BlockException e) {
            String msg = "method: [" + getSimplifiedMethodName(abstractMethod) + "] invoke was blocked by sentinel.";

            rootLogger.warn(msg, e);
            throw new TraceException(msg, e);
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

    public void setResult(boolean result) {
        this.result = result;
    }

    public boolean isSentinel() {
        return sentinel;
    }

    public void setSentinel(boolean sentinel) {
        this.sentinel = sentinel;
    }

    public void setTraceConfigs(ConcurrentMap<String, TraceConfig> traceConfigs) {
        this.traceConfigs = traceConfigs;
    }

    public void setBizLoggerName(String bizLoggerName) {
        this.defaultBizLogger = LoggerFactory.getLogger(bizLoggerName);
    }

    public void setBizLogger(Logger bizLogger) {
        this.defaultBizLogger = bizLogger;
    }
}
