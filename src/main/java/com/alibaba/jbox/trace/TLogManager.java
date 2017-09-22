package com.alibaba.jbox.trace;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.jbox.executor.AsyncRunnable;
import com.alibaba.jbox.executor.ExecutorManager;
import com.alibaba.jbox.utils.DateUtils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.core.io.Resource;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static com.alibaba.jbox.trace.TraceAspect.traceLogger;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/22 15:50:00.
 */
public class TLogManager {

    private final ConcurrentMap<String, List<String>> METHOD_SPEL_MAP = new ConcurrentHashMap<>();

    private final Logger tLogger = (Logger)LoggerFactory.getLogger(TLogManager.class);

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private static final String SEPARATOR = "|";

    private static final String PLACEHOLDER = "";

    private static ExecutorService EXECUTOR;

    private boolean sync = true;

    private String charset = "UTF-8";

    public TLogManager(String filePath) {
        initTLogger(filePath);
        if (sync) {
            ExecutorManager.setSyncInvoke(true);
        }
        EXECUTOR = ExecutorManager.newFixedMinMaxThreadPool("TLogManager", 3, 12, 1024);
    }

    void postTLogEvent(TLogEvent event) {
        EXECUTOR.submit(new TLogEventParser(event));
    }

    private final class TLogEventParser implements AsyncRunnable {

        private TLogEvent event;

        TLogEventParser(TLogEvent event) {
            this.event = event;
        }

        @Override
        public void execute() {
            List<Object> logEntity = new ArrayList<>();
            logEntity.add(event.getServerIp());
            logEntity.add(event.getTraceId());
            logEntity.add(event.getClientName());
            logEntity.add(event.getClientIp());
            logEntity.add(DateUtils.millisTimeFormat(event.getInvokeTime()));
            logEntity.add(event.getCostTime());
            logEntity.add(event.getClassName());
            logEntity.add(event.getMethodName());
            logEntity.add(JSONObject.toJSONString(event.getArgs()));
            logEntity.add(JSONObject.toJSONString(event.getResult()));
            logEntity.add(event.getException() == null ? PLACEHOLDER : JSONObject.toJSONString(event.getException()));

            String methodKey = event.getClassName() + ":" + event.getMethodName();
            List<String> spels = METHOD_SPEL_MAP.getOrDefault(methodKey, Collections.emptyList());
            List<Object> spelValues = calcSpelValues(event.getArgs(), event.getResult(), spels);
            for (Object spelValue : spelValues) {
                if (spelValue instanceof String) {
                    logEntity.add(spelValue);
                } else {
                    logEntity.add(JSONObject.toJSONString(spelValue));
                }
            }

            String logContent = Joiner.on(SEPARATOR).join(logEntity);
            TLogManager.this.tLogger.trace("{}", logContent);
        }

        @Override
        public String taskInfo() {
            return MessageFormatter.format("TLogEventParser {}", event).getMessage();
        }
    }

    private static List<Object> calcSpelValues(Object[] args, Object result, List<String> spels) {
        try {
            List<Object> values;
            if (spels != null && !spels.isEmpty()) {
                // 将'args', 'result'导入spel执行环境
                EvaluationContext context = new StandardEvaluationContext();
                context.setVariable("args", args);
                context.setVariable("result", result);

                values = new ArrayList<>(args.length + 1);
                for (String spel : spels) {
                    Object judgeResult = SPEL_PARSER.parseExpression(spel).getValue(context);
                    values.add(judgeResult);
                }
            } else {
                values = Collections.emptyList();
            }
            return values;
        } catch (Throwable e) {
            traceLogger.error("parse spels: {} error", spels, e);
            throw e;
        }
    }

    private void initTLogger(String filePath) {
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(tLogger.getLoggerContext());
        appender.setFile(filePath);
        appender.setAppend(true);

        TimeBasedRollingPolicy rolling = new TimeBasedRollingPolicy();
        rolling.setParent(appender);
        rolling.setFileNamePattern(filePath + ".%d{yyyy-MM-dd}");
        rolling.setContext(tLogger.getLoggerContext());
        rolling.start();
        appender.setRollingPolicy(rolling);

        PatternLayoutEncoder layout = new PatternLayoutEncoder();
        layout.setPattern("%m%n");
        layout.setCharset(Charset.forName(charset));
        layout.setContext(tLogger.getLoggerContext());
        layout.start();
        appender.setEncoder(layout);

        appender.start();

        tLogger.detachAndStopAllAppenders();
        tLogger.addAppender(appender);
    }

    public void setSpelResource(Resource jsonResource) throws IOException {
        String json = CharStreams.toString(new InputStreamReader(jsonResource.getInputStream()));
        JSONObject jsonObject = JSONObject.parseObject(json);
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            List<String> spels = METHOD_SPEL_MAP.computeIfAbsent(key, k -> new LinkedList<>());
            ((JSONArray)entry.getValue()).stream().map(v -> (String)v).forEach(spels::add);
        }
    }

    public void setMethodSpelMap(Map<String, List<String>> methodSpelMap) {
        METHOD_SPEL_MAP.putAll(methodSpelMap);
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }
}
