package com.alibaba.jbox.trace;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.base.Preconditions;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ReflectionUtils;

import static com.alibaba.jbox.trace.TraceConstants.KEY_ARGS;
import static com.alibaba.jbox.trace.TraceConstants.KEY_PH;
import static com.alibaba.jbox.trace.TraceConstants.KEY_PLACEHOLDER;
import static com.alibaba.jbox.trace.TraceConstants.KEY_RESULT;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/23 08:03:00.
 */
public class SpELHelpers {

    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private static List<Method> CUSTOM_METHODS = new CopyOnWriteArrayList<>();

    static {
        Method isNotEmpty = ReflectionUtils.findMethod(SpELHelpers.class, "isNotEmpty", Collection.class);
        Method ifNotEmptyGet = ReflectionUtils.findMethod(SpELHelpers.class, "ifNotEmptyGet", List.class, int.class);
        Method ifEmptyGetDefault = ReflectionUtils.findMethod(SpELHelpers.class, "ifEmptyGetDefault", List.class,
            Object.class, int.class);

        registerFunction(isNotEmpty);
        registerFunction(ifNotEmptyGet);
        registerFunction(ifEmptyGetDefault);
    }

    public static void registerFunction(Method staticMethod) {
        Preconditions.checkArgument(Modifier.isStatic(staticMethod.getModifiers()),
            "method [%s] is not static, SpEL is not support.", staticMethod);

        CUSTOM_METHODS.add(staticMethod);
    }

    static List<Object> calcSpelValues(LogEvent event, List<String> spels, String placeHolder) {
        List<Object> values;
        if (spels != null && !spels.isEmpty()) {
            // 将'args'、'result'、'placeholder'、'ph'导入spel执行环境
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable(KEY_ARGS, event.getArgs());
            context.setVariable(KEY_RESULT, event.getResult());
            context.setVariable(KEY_PLACEHOLDER, placeHolder);
            context.setVariable(KEY_PH, placeHolder);
            // 将自定义函数导入spel执行环境
            registerFunctions(context);
            values = new ArrayList<>(spels.size());
            for (String spel : spels) {
                Object judgeResult = SPEL_PARSER.parseExpression(spel).getValue(context);
                values.add(judgeResult);
            }
        } else {
            values = Collections.emptyList();
        }
        return values;
    }

    static List<Object> calcSpelValues(Object obj, List<String> spels) {
        List<Object> values = new ArrayList<>(spels.size());
        // 将自定义函数导入spel执行环境
        StandardEvaluationContext context = new StandardEvaluationContext();
        registerFunctions(context);
        for (String spel : spels) {
            Object result = SPEL_PARSER.parseExpression(spel).getValue(context, obj);
            values.add(result);
        }

        return values;
    }

    static void registerFunctions(StandardEvaluationContext context) {
        for (Method method : CUSTOM_METHODS) {
            context.registerFunction(method.getName(), method);
        }
    }

    /**
     * 注册到SpEL环境中的函数, 尽量减少其他的依赖
     *
     * @param collection
     * @param <T>
     * @return
     */
    public static <T> Collection<T> isNotEmpty(Collection<T> collection) {
        if (collection != null && !collection.isEmpty()) {
            return collection;
        }
        return null;
    }

    public static <T> T ifNotEmptyGet(List<T> list, int index) {
        list = (List<T>)isNotEmpty(list);
        return list == null ? null : list.get(index);
    }

    public static <T> T ifEmptyGetDefault(List<T> list, T defaultObj, int index) {
        T obj = ifNotEmptyGet(list, index);
        return obj == null ? defaultObj : obj;
    }
}
