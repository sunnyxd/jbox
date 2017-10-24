package com.alibaba.jbox.trace;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

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
        Method[] methods = SpELHelpers.class.getMethods();
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers())
                && Modifier.isPublic(method.getModifiers())
                && !"registerFunction".equals(method.getName())) {

                registerFunction(method);
            }
        }
    }

    /**
     * 开放给外界可导入自定义工具函数, 以减少在xml/json/groovy/ELConfig中书写的spel复杂度
     *
     * @param staticMethod: 注册到spel环境中的函数(必须为public、static修饰)
     */
    public static void registerFunction(Method staticMethod) {
        if (!Modifier.isStatic(staticMethod.getModifiers())
            || !Modifier.isPublic(staticMethod.getModifiers())) {

            throw new IllegalArgumentException(
                String.format("method [%s] is not static, SpEL is not support.", staticMethod)
            );
        }

        CUSTOM_METHODS.add(staticMethod);
    }

    public static void registerFunctions(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                && Modifier.isPublic(method.getModifiers())) {

                registerFunction(method);
            }
        }
    }

    /**
     * 将args、result、placeholder导入环境, 计算spel表达式
     *
     * @param event
     * @param spels
     * @param placeHolder
     * @return
     */
    static List<Object> evalSpelWithEvent(LogEvent event, List<String> spels, String placeHolder) {
        List<Object> values;
        if (spels != null && !spels.isEmpty()) {
            // 将'args'、'result'、'placeholder'、'ph'导入spel执行环境
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable(KEY_ARGS, event.getArgs());
            context.setVariable(KEY_RESULT, event.getResult());
            context.setVariable(KEY_PLACEHOLDER, placeHolder);
            context.setVariable(KEY_PH, placeHolder);
            // 将自定义函数导入spel执行环境
            prepareFunctions(context);

            values = new ArrayList<>(spels.size());
            for (String spel : spels) {
                Object evalResult = SPEL_PARSER.parseExpression(spel).getValue(context);
                values.add(evalResult);
            }
        } else {
            values = Collections.emptyList();
        }
        return values;
    }

    /**
     * 不将args、result导入环境, 直接针对object计算spel表达式
     *
     * @param obj
     * @param spels
     * @return
     */
    static List<Object> evalSpelWithObject(Object obj, List<String> spels, String placeHolder) {
        List<Object> values;
        if (spels != null && !spels.isEmpty()) {
            StandardEvaluationContext context = new StandardEvaluationContext();
            // 仅将'placeholder'、'ph'导入spel执行环境
            context.setVariable(KEY_PLACEHOLDER, placeHolder);
            context.setVariable(KEY_PH, placeHolder);
            // 将自定义函数导入spel执行环境
            prepareFunctions(context);

            values = new ArrayList<>(spels.size());
            for (String spel : spels) {
                Object result = SPEL_PARSER.parseExpression(spel).getValue(context, obj);
                values.add(result);
            }
        } else {
            values = Collections.emptyList();
        }

        return values;
    }

    private static void prepareFunctions(StandardEvaluationContext context) {
        for (Method method : CUSTOM_METHODS) {
            context.registerFunction(method.getName(), method);
        }
    }

    /** ****************************************** **/
    /**  默认注册到SpEL环境中的工具函数(尽量减少额外依赖)  **/
    /************************************************/
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

    public static <T> T ifEmptyGetDefault(List<T> list, int index, T defaultObj) {
        T obj = ifNotEmptyGet(list, index);
        return obj == null ? defaultObj : obj;
    }
}
