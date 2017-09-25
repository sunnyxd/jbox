package com.alibaba.jbox.trace;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ReflectionUtils;

import static com.alibaba.jbox.trace.Constants.KEY_ARGS;
import static com.alibaba.jbox.trace.Constants.KEY_PH;
import static com.alibaba.jbox.trace.Constants.KEY_PLACEHOLDER;
import static com.alibaba.jbox.trace.Constants.KEY_RESULT;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/23 08:03:00.
 */
class SpELHelpers {

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private static Set<Method> SPEL_METHODS = new ConcurrentSkipListSet<>();

    static {
        Method isNotEmpty = ReflectionUtils.findMethod(SpELHelpers.class, "isNotEmpty", Collection.class);
        SPEL_METHODS.add(isNotEmpty);
    }

    static List<Object> calcSpelValues(TLogEvent event, List<String> spels, String placeHolder) {
        List<Object> values;
        if (spels != null && !spels.isEmpty()) {
            // 将'args'、'result'、'placeholder'、'ph'导入spel执行环境
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable(KEY_ARGS, event.getArgs());
            context.setVariable(KEY_RESULT, event.getResult());
            context.setVariable(KEY_PLACEHOLDER, placeHolder);
            context.setVariable(KEY_PH, placeHolder);
            // 将自定义函数导入spel执行环境
            for (Method method : SPEL_METHODS) {
                context.registerFunction(method.getName(), method);
            }
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
        for (String spel : spels) {
            Object result = SPEL_PARSER.parseExpression(spel).getValue(obj);
            values.add(result);
        }

        return values;
    }

    public static <T> Collection<T> isNotEmpty(Collection<T> collection) {
        if (collection != null && !collection.isEmpty()) {
            return collection;
        }
        return null;
    }
}
