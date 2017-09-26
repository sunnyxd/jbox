package com.alibaba.jbox.trace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.jbox.executor.AsyncRunnable;
import com.alibaba.jbox.executor.ExecutorManager;
import com.alibaba.jbox.utils.DateUtils;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.InitializingBean;

import static com.alibaba.jbox.trace.Constants.SEPARATOR;
import static com.alibaba.jbox.trace.Constants.TLOG_EXECUTOR_GROUP;
import static com.alibaba.jbox.trace.LogBackHelper.initTLogger;
import static com.alibaba.jbox.trace.SpELHelpers.calcSpelValues;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/22 15:50:00.
 */
public class TLogManager extends TLogManagerConfig implements InitializingBean {

    private static final long serialVersionUID = -4553832981389212025L;

    private static ExecutorService executor;

    private Logger tLogger;

    public TLogManager() {
        super();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // init executor
        if (executor == null) {
            ExecutorManager.setSyncInvoke(TLOG_EXECUTOR_GROUP, isSync());

            executor = ExecutorManager.newFixedMinMaxThreadPool(
                TLOG_EXECUTOR_GROUP, getMinPoolSize(), getMaxPoolSize(),
                getRunnableQSize());
        }

        // init tLogger
        if (tLogger == null) {
            tLogger = initTLogger(getUniqueLoggerName(), getFilePath(), getCharset(), getMaxHistory(), getFilters());
        }
    }

    void postTLogEvent(TLogEvent event) {
        executor.submit(new TLogEventParser(event));
    }

    final class TLogEventParser implements AsyncRunnable {

        private TLogEvent event;

        TLogEventParser(TLogEvent event) {
            this.event = event;
        }

        @Override
        public void execute() {
            List<Object> logEntity = new ArrayList<>();
            logEntity.add(DateUtils.millisFormatFromMillis(event.getInvokeTime()));
            logEntity.add(event.getInvokeThread());
            logEntity.add(event.getCostTime());
            logEntity.add(event.getClassName());
            logEntity.add(event.getMethodName());
            logEntity.add(nullToPlaceholder(event.getArgs(), JSONObject::toJSONString));
            logEntity.add(nullToPlaceholder(event.getResult(), JSONObject::toJSONString));
            logEntity.add(nullToPlaceholder(event.getException(), JSONObject::toJSONString));
            logEntity.add(event.getServerIp());
            logEntity.add(nullToPlaceholder(event.getTraceId()));
            logEntity.add(event.getClientName());
            logEntity.add(event.getClientIp());

            String methodKey = event.getClassName() + ":" + event.getMethodName();
            List<SpELConfigEntry> spels = getMETHOD_SPEL_MAP().getOrDefault(methodKey, Collections.emptyList());

            List<Collection> collectionArgValues = spels.stream().filter(SpELConfigEntry::isMulti).findAny()
                .map(multiEntry -> parsMultiConfig(new ArrayList<>(spels), multiEntry, event))
                .orElseGet(() -> parsSingleConfig(spels, event));

            // 针对每一个为Collection的#arg都打一条log
            for (Collection collectionArgValue : collectionArgValues) {
                LinkedList<Object> logEntityCopy = new LinkedList<>(logEntity);
                logEntityCopy.addAll(collectionArgValue);

                String content = Joiner.on(SEPARATOR).useForNull(getPlaceHolder()).join(logEntityCopy);
                TLogManager.this.tLogger.trace("{}", content);
            }
        }

        @Override
        public String taskInfo() {
            return MessageFormatter.format("TLogEventParser {}", event).getMessage();
        }
    }

    private List<Collection> parsMultiConfig(List<SpELConfigEntry> spels, SpELConfigEntry multiConfigEntry,
                                             TLogEvent event) {
        List<String> spelList = spels.stream().filter(entry -> !entry.isMulti()).map(SpELConfigEntry::getKey).collect(
            Collectors.toList());
        List<Object> spelValues = calcSpelValues(event, spelList, getPlaceHolder());

        String argKey = multiConfigEntry.getKey();
        Object multiArg = calcSpelValues(event, Collections.singletonList(argKey), getPlaceHolder()).get(0);

        Collection collectionArg;
        if (multiArg instanceof Collection) {
            collectionArg = (Collection)multiArg;
        } else if (multiArg.getClass().isArray()) {
            collectionArg = Arrays.stream((Object[])multiArg).collect(Collectors.toList());
        } else {
            throw new TraceException(
                argKey + " specified argument is not an array or Collection instance");
        }

        List<Collection> collectionArgResults = new ArrayList<>();
        List<String> argInnerSpels = multiConfigEntry.getValue();
        for (Object collectionArgItem : collectionArg) {
            Collection<Object> innerCollection = new ArrayList<>(spelValues);
            if (argInnerSpels.isEmpty()) {
                innerCollection.add(collectionArgItem);
            } else {
                innerCollection.addAll(calcSpelValues(collectionArgItem, argInnerSpels));
            }
            collectionArgResults.add(innerCollection);
        }

        return collectionArgResults;
    }

    private List<Collection> parsSingleConfig(List<SpELConfigEntry> singleSpels, TLogEvent event) {
        List<String> argSpels = singleSpels.stream().map(SpELConfigEntry::getKey).collect(Collectors.toList());

        return Collections.singletonList(calcSpelValues(event, argSpels, getPlaceHolder()));
    }

    private String nullToPlaceholder(Object nullableObj) {
        return nullToPlaceholder(nullableObj, Object::toString);
    }

    private String nullToPlaceholder(Object nullableObj, Function<Object, String> processor) {
        return nullableObj == null ? getPlaceHolder() : processor.apply(nullableObj);
    }
}
