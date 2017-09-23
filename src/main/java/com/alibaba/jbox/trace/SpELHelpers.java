package com.alibaba.jbox.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

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

    static List<Object> calcSpelValues(TLogEvent event, List<String> spels, String placeHolder) {
        List<Object> values;
        if (spels != null && !spels.isEmpty()) {
            // 将'args'、'result'、'placeholder'、'ph'导入spel执行环境
            EvaluationContext context = new StandardEvaluationContext();
            context.setVariable(KEY_ARGS, event.getArgs());
            context.setVariable(KEY_RESULT, event.getResult());
            context.setVariable(KEY_PLACEHOLDER, placeHolder);
            context.setVariable(KEY_PH, placeHolder);

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

    static List<String> calcSpelValues(Object obj, List<String> spels) {
        List<String> values = new ArrayList<>(spels.size());
        for (String spel : spels) {
            String result = SPEL_PARSER.parseExpression(spel).getValue(obj, String.class);
            values.add(result);
        }

        return values;
    }
}
