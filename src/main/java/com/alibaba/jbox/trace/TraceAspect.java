package com.alibaba.jbox.trace;

import com.alibaba.jbox.annotation.NotEmpty;
import com.alibaba.jbox.annotation.NotNull;
import com.alibaba.jbox.utils.JboxUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.taobao.eagleeye.EagleEye;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author jifang
 * @version 1.2
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

    private static final ConcurrentMap<Class, List<Object>> primitiveDefaultValues = new ConcurrentHashMap<>();

    private static final ConcurrentMap<Class<?>, Pair<List<Field>, List<Field>>> notNullEmptyFields = new ConcurrentHashMap<>();

    // <package.class.method, logger>
    private static final ConcurrentMap<String, Logger> bizLoggers = new ConcurrentHashMap<>();

    static {
        primitiveDefaultValues.put(byte.class, Arrays.asList(-1, 0));
        primitiveDefaultValues.put(Byte.class, Arrays.asList(-1, 0, null));
        primitiveDefaultValues.put(short.class, Arrays.asList(-1, 0));
        primitiveDefaultValues.put(Short.class, Arrays.asList(-1, 0, null));
        primitiveDefaultValues.put(int.class, Arrays.asList(-1, 0));
        primitiveDefaultValues.put(Integer.class, Arrays.asList(-1, 0, null));
        primitiveDefaultValues.put(long.class, Arrays.asList(-1, 0));
        primitiveDefaultValues.put(Long.class, Arrays.asList(-1, 0, null));
        primitiveDefaultValues.put(float.class, Collections.singletonList(0.0f));
        primitiveDefaultValues.put(Float.class, Arrays.asList(0.0f, null));
        primitiveDefaultValues.put(double.class, Collections.singletonList(0.0d));
        primitiveDefaultValues.put(Double.class, Arrays.asList(0.0d, null));
        primitiveDefaultValues.put(boolean.class, Collections.singletonList(false));
        primitiveDefaultValues.put(Boolean.class, Arrays.asList(false, null));
        primitiveDefaultValues.put(char.class, Collections.singletonList(0));
        primitiveDefaultValues.put(Character.class, Arrays.asList(0, null));
    }

    private volatile boolean paramCheck = false;

    public TraceAspect() {
        this(false);
    }

    public TraceAspect(boolean paramCheck) {
        this.paramCheck = paramCheck;
    }

    @Around("@annotation(com.alibaba.jbox.trace.Trace)")
    public Object invoke(final ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. put traceId
        MDC.put(TRACE_ID, EagleEye.getTraceId());
        try {
            long start = System.currentTimeMillis();
            Method method = JboxUtils.getRealMethod(joinPoint);
            Object[] args = joinPoint.getArgs();
            // 2. check arguments
            if (paramCheck) {
                checkArgumentsNotNullOrEmpty(method, args);
            }

            Object result = joinPoint.proceed(args);

            Trace trace = method.getAnnotation(Trace.class);
            if (trace != null) {
                long costTime = System.currentTimeMillis() - start;

                // 3. log over time
                if (isNeedLogger(trace, costTime)) {
                    String logContent = buildLogContent(method, costTime, trace, args);

                    logBiz(logContent, method, joinPoint, trace);
                    logTrace(logContent);
                }
            }

            return result;
        } catch (Throwable e) {
            rootLogger.error("", e);
            throw e;
        } finally {
            // 4. remove traceId
            MDC.remove(TRACE_ID);
        }
    }

    /*** **************************** ***/
    /***  check arguments @since 1.2  ***/
    /*** **************************** ***/
    private void checkArgumentsNotNullOrEmpty(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; ++i) {
            Parameter parameter = parameters[i];
            String paramName = parameter.getName();
            Object arg = args[i];

            NotNull notNull = parameter.getAnnotation(NotNull.class);
            if (notNull != null) {
                checkNotNull(arg, method, notNull.name(), paramName);
            }

            NotEmpty notEmpty = parameter.getAnnotation(NotEmpty.class);
            if (notEmpty != null) {
                checkNotEmpty(arg, parameter.getType(), method, notEmpty.name(), paramName);
            }

            Pair<List<Field>, List<Field>> pair = getNotNullEmptyFields(arg);
            // 需要check argument的内部DTO属性
            if (!pair.getLeft().isEmpty() || !pair.getRight().isEmpty()) {
                checkField(pair, arg, paramName, method);
            }
        }
    }

    private void checkNotNull(Object arg, Method method, String annotationName, String paramName) {
        Preconditions.checkArgument(arg != null,
                String.format("method [%s]'s param [%s] can not be null",
                        method,
                        Strings.isNullOrEmpty(annotationName) ? paramName : annotationName)
        );
    }

    private void checkNotEmpty(Object arg, Class<?> paramType, Method method, String annotationName, String paramName) {
        // first check not null
        checkNotNull(arg, method, annotationName, paramName);

        // after check not empty
        Preconditions.checkArgument(
                objectNotEmpty(paramType, arg),
                String.format("method [%s]'s param [%s] can not be empty",
                        method,
                        Strings.isNullOrEmpty(annotationName) ? paramName : annotationName)
        );
    }

    private void checkField(Pair<List<Field>, List<Field>> pair, Object arg, String paramName, Method method) {
        // check not null
        for (Field notNullField : pair.getLeft()) {
            ReflectionUtils.makeAccessible(notNullField);
            Object fieldValue = ReflectionUtils.getField(notNullField, arg);
            checkFieldNotNull(fieldValue, method, paramName, notNullField.getAnnotation(NotNull.class).name(), notNullField.getName());
        }

        // check not empty
        for (Field notEmptyField : pair.getRight()) {
            ReflectionUtils.makeAccessible(notEmptyField);

            String notEmptyFieldName = notEmptyField.getAnnotation(NotEmpty.class).name();
            String fieldName = notEmptyField.getName();

            Object fieldValue = ReflectionUtils.getField(notEmptyField, arg);
            checkFieldNotNull(fieldValue, method, paramName, notEmptyFieldName, fieldName);
            checkFieldNotEmpty(fieldValue, method, paramName, notEmptyFieldName, fieldName);
        }
    }

    private void checkFieldNotEmpty(Object value, Method method, String paramName, String annotationName, String fieldName) {
        Preconditions.checkArgument(objectNotEmpty(value.getClass(), value),
                String.format("method [%s]'s param [%s]'s field [%s] can not be empty",
                        method,
                        paramName,
                        Strings.isNullOrEmpty(annotationName) ? fieldName : annotationName));

    }


    private void checkFieldNotNull(Object value, Method method, String paramName, String annotationName, String fieldName) {
        Preconditions.checkArgument(value != null,
                String.format("method [%s]'s param [%s]'s field [%s] can not be null",
                        method,
                        paramName,
                        Strings.isNullOrEmpty(annotationName) ? fieldName : annotationName));
    }

    private Pair<List<Field>, List<Field>> getNotNullEmptyFields(Object arg) {
        return notNullEmptyFields.computeIfAbsent(arg.getClass(), (paramType) -> {
            List<Field> notNullFields = FieldUtils.getFieldsListWithAnnotation(paramType, NotNull.class);
            List<Field> notEmptyFields = FieldUtils.getFieldsListWithAnnotation(paramType, NotEmpty.class);

            return Pair.of(new CopyOnWriteArrayList<>(notNullFields), new CopyOnWriteArrayList<>(notEmptyFields));
        });
    }

    private boolean objectNotEmpty(Class<?> paramType, Object arg) {
        List<Object> defaultValues = primitiveDefaultValues.get(paramType);
        if (defaultValues != null) {
            return !defaultValues.contains(arg);
        } else if (String.class.isAssignableFrom(paramType)) {
            return !Strings.isNullOrEmpty((String) arg);
        } else if (Collection.class.isAssignableFrom(paramType)) {
            return !((Collection) arg).isEmpty();
        } else if (Map.class.isAssignableFrom(paramType)) {
            return !((Map) arg).isEmpty();
        }

        return true;
    }

    /*** ******************************* ***/
    /***  append cost logger @since 1.1  ***/
    /*** ******************************* ***/

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

    public boolean isParamCheck() {
        return paramCheck;
    }

    public void setParamCheck(boolean paramCheck) {
        this.paramCheck = paramCheck;
    }
}
