package com.alibaba.jbox.trace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;

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
import org.springframework.util.ReflectionUtils;

import static com.alibaba.jbox.trace.TraceConstants.CONFIG_KEY_PATTERN;
import static com.alibaba.jbox.trace.TraceConstants.TRACE_ID;
import static com.alibaba.jbox.trace.TraceConstants.tracer;
import static com.alibaba.jbox.utils.JboxUtils.getAbstractMethod;
import static com.alibaba.jbox.utils.JboxUtils.getImplMethod;
import static com.alibaba.jbox.utils.JboxUtils.getSimplifiedMethodName;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.8
 *          - 1.0: append 'traceId' to logger;
 *          - 1.1: append 'method invoke cost time & param' to biz logger;
 *          - 1.2: validate method param {@code com.alibaba.jbox.annotation.NotNull}, {@code
 *          com.alibaba.jbox.annotation.NotEmpty};
 *          - 1.3: replace validator instance to hibernate-validator;
 *          - 1.4: add sentinel on invoked service interface;
 *          - 1.5: append method invoke result on logger content;
 *          - 1.6: support use TraceAspect not with {@code com.alibaba.jbox.trace.TraceAspect} annotation.
 *          - 1.7: async/sync append |invokeTime|thread|rt|class|method|args|result|exception|serverIp|traceId
 *          |clientName|clientIp| to TLog.
 *          - 1.8: add 'errorRoot' config to determine append root logger error content.
 * @since 2016/11/25 上午11:53.
 */
@Aspect
public class TraceAspect extends AbstractTraceConfig {

    private static final long serialVersionUID = 1383288704716921329L;

    private static final Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    private static final ConcurrentMap<String, Logger> BIZ_LOGGERS = new ConcurrentHashMap<>();

    @Around("@annotation(com.alibaba.jbox.trace.Trace)")
    public Object invoke(final ProceedingJoinPoint joinPoint) throws Throwable {

        /**
         * @since 1.0: put traceId
         */
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }

        LogEvent logEvent = new LogEvent();
        try {
            // start time
            long start = System.currentTimeMillis();
            logEvent.setStartTime(start);

            // class、method、configKey
            Method method = isUseAbstractMethod() ? getAbstractMethod(joinPoint) : getImplMethod(joinPoint);
            String className = method.getDeclaringClass().getName();
            String methodName = method.getName();
            String configKey = String.format(CONFIG_KEY_PATTERN, className, methodName);

            logEvent.setClassName(className);
            logEvent.setMethodName(methodName);
            logEvent.setConfigKey(configKey);

            // args
            Object[] args = joinPoint.getArgs();
            logEvent.setArgs(args);

            /**
             * @since 1.2 validate arguments
             */
            if (isValidator()) {
                validateArguments(joinPoint.getTarget(), method, args);
            }

            /**
             * @since 1.4 sentinel
             */
            if (isSentinel()) {
                entry(method);
            }

            // result
            Object result = joinPoint.proceed(args);
            logEvent.setResult(result);

            long rt = System.currentTimeMillis() - start;
            logEvent.setRt(rt);

            /**
             * @since 1.1 logger invoke elapsed time & parameters
             */
            if (isElapsed()) {
                Object[] traceConfig = getTraceConfig(configKey, method.getAnnotation(Trace.class));
                if (isNeedLogger((Long)traceConfig[0], rt)) {
                    String logContent = buildLogContent(method, rt, args, result);

                    logBiz(logContent, configKey, method, joinPoint, (String)traceConfig[1]);
                    logTrace(logContent);
                }
            }
            sendLogEvent(logEvent);

            return result;
        } catch (Throwable e) {
            if (isErrorRoot()) {
                rootLogger.error("method: [{}] invoke failed, traceId:{}",
                    getSimplifiedMethodName(JboxUtils.getAbstractMethod(joinPoint)),
                    Strings.isNullOrEmpty(traceId) ? "" : traceId,
                    e);
            }
            logEvent.setException(e);
            sendLogEvent(logEvent);

            throw e;
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
        }
    }

    /*** *********************************************** ***/
    /***  send metadata to LogEventManager   @since 1.7  ***/
    /*** *********************************************** ***/
    private void sendLogEvent(LogEvent event) {
        if (!getTLogManagers().isEmpty()) {
            event.init();
            for (TLogManager tLogManager : getTLogManagers()) {
                tLogManager.postLogEvent(event);
            }
        }
    }

    /*** ***************************************************************************** ***/
    /***  user 'com.alibaba.jbox.trace.TraceConfig' like as '@Trace' config @since 1.6 ***/
    /*** ***************************************************************************** ***/
    private Object[] getTraceConfig(String configKey, Trace trace) {

        long threshold;
        String loggerName;
        if (trace == null) {
            TraceConfig config = this.getTraceConfigs().computeIfAbsent(configKey, key -> new TraceConfig());
            threshold = config.getThreshold();
            loggerName = config.getLogger();
        } else {
            threshold = trace.threshold();
            loggerName = trace.value();
        }

        return new Object[] {threshold, loggerName};
    }

    /*** *********************************************** ***/
    /***  validator arguments with Validator @since 1.3  ***/
    /*** *********************************************** ***/
    private static class InnerValidator {
        private static final ExecutableValidator validator;

        static {
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            validator = factory.getValidator().forExecutables();
        }
    }

    private void validateArguments(Object target, Method method, Object[] args) {
        Set<ConstraintViolation<Object>> violationSet = InnerValidator.validator.validateParameters(target, method,
            args);
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
            if (!Modifier.isPrivate(abstractMethod.getModifiers()) && !Modifier.isProtected(
                abstractMethod.getModifiers())) {
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

    /*** ******************************* ***/
    /***  append cost logger @since 1.1  ***/
    /*** ******************************* ***/

    private boolean isNeedLogger(long threshold, long rt) {
        return rt > threshold;
    }

    private String buildLogContent(Method method, long costTime, Object[] args, Object resultObj) {
        StringBuilder logBuilder = new StringBuilder(120);
        logBuilder
            .append("method: [")
            .append(getSimplifiedMethodName(method))
            .append("] invoke rt [")
            .append(costTime)
            .append("]ms");

        if (isParam()) {
            logBuilder.append(", params:")
                .append(Arrays.toString(args));
        }

        // @since 1.5
        if (isResult()) {
            logBuilder.append(", result: ")
                .append(JSONObject.toJSONString(resultObj));
        }

        return logBuilder.toString();
    }

    private void logBiz(String logContent, String configKey, Method method, Object target, String loggerName) {
        Class<?> clazz = method.getDeclaringClass();
        Logger bizLogger = BIZ_LOGGERS.computeIfAbsent(configKey, key -> {
            try {
                if (Strings.isNullOrEmpty(loggerName)) {
                    return getDefaultBizLogger() != null ? getDefaultBizLogger() : getClassInnerLogger(clazz, target);
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

    private Logger getClassInnerLogger(Class<?> clazz, Object target) throws IllegalAccessException {
        Logger bizLogger = null;
        for (Field field : clazz.getDeclaredFields()) {
            if (Logger.class.isAssignableFrom(field.getType())) {
                if (bizLogger == null) {
                    field.setAccessible(true);
                    bizLogger = (Logger)field.get(target);
                } else {
                    throw new TraceException(
                        "duplicated field's type is 'org.slf4j.Logger', please specify the used Logger name in @Trace"
                            + ".name()");
                }
            }
        }

        return bizLogger;
    }

    private Logger getNamedBizLogger(String loggerName, Class<?> clazz, Object target) {
        try {
            Field loggerField = ReflectionUtils.findField(clazz, loggerName);
            return (Logger)ReflectionUtils.getField(loggerField, target);
        } catch (IllegalStateException e) {
            throw new TraceException(
                "not such 'org.slf4j.Logger' instance named [" + loggerName + "], in class [" + clazz.getName() + "]",
                e);
        }
    }

    private void logTrace(String logContent) {
        if (isTrace()) {
            tracer.info(logContent);
        }
    }
}
