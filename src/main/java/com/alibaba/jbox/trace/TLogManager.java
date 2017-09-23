package com.alibaba.jbox.trace;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.jbox.executor.AsyncRunnable;
import com.alibaba.jbox.executor.ExecutorManager;
import com.alibaba.jbox.utils.DateUtils;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;

import static com.alibaba.jbox.trace.Constants.PLACEHOLDER;
import static com.alibaba.jbox.trace.Constants.SEPARATOR;
import static com.alibaba.jbox.trace.Constants.UTF_8;
import static com.alibaba.jbox.trace.LogBackHelper.initTLogger;
import static com.alibaba.jbox.trace.SpELHelpers.calcSpelValues;
import static com.alibaba.jbox.trace.TraceAspect.traceLogger;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/22 15:50:00.
 */
public class TLogManager implements InitializingBean {

    private final ConcurrentMap<String, List<SpELConfigEntry>> METHOD_SPEL_MAP = new ConcurrentHashMap<>();

    private final Logger tLogger = LoggerFactory.getLogger(TLogManager.class);

    private static ExecutorService EXECUTOR;

    private String placeHolder = PLACEHOLDER;

    private String filePath;

    private boolean sync = true;

    private String charset = UTF_8;

    @PostConstruct
    @Override
    public void afterPropertiesSet() throws Exception {
        initTLogger(tLogger, filePath, charset);
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
            try {
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
                List<SpELConfigEntry> spels = METHOD_SPEL_MAP.getOrDefault(methodKey, Collections.emptyList());

                List<Collection> collectionArgValues = spels.stream().filter(SpELConfigEntry::isMulti).findAny()
                    .map(multiEntry -> parsMultiConfig(new ArrayList<>(spels), multiEntry, event))
                    .orElseGet(() -> parsSingleConfig(spels, event));

                // 针对每一个为Collection的#arg都打一条log
                for (Collection collectionArgValue : collectionArgValues) {
                    LinkedList<Object> logEntityCopy = new LinkedList<>(logEntity);
                    logEntityCopy.addAll(collectionArgValue);

                    String content = Joiner.on(SEPARATOR).useForNull(placeHolder).join(logEntityCopy);
                    TLogManager.this.tLogger.trace("{}", content);
                }
            } catch (Throwable e) {
                traceLogger.error("trace Event:[{}] error.", event, e);
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
        List<Object> spelValues = calcSpelValues(event, spelList, placeHolder);

        String argKey = multiConfigEntry.getKey();
        Object multiArg = calcSpelValues(event, Collections.singletonList(argKey), placeHolder).get(
            0);

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

        return Collections.singletonList(calcSpelValues(event, argSpels, placeHolder));
    }

    private String nullToPlaceholder(Object nullableObj) {
        return nullToPlaceholder(nullableObj, Object::toString);
    }

    private String nullToPlaceholder(Object nullableObj, Function<Object, String> processor) {
        return nullableObj == null ? placeHolder : processor.apply(nullableObj);
    }

    public void setSpelResource(Resource jsonResource) throws IOException {
        String json = CharStreams.toString(new InputStreamReader(jsonResource.getInputStream()));
        JSONObject jsonObject = JSONObject.parseObject(json);
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            List<SpELConfigEntry> spels = METHOD_SPEL_MAP.computeIfAbsent(key, k -> new LinkedList<>());
            spels.addAll(SpELConfigEntry.parseEntriesFromJsonArray((JSONArray)entry.getValue()));
        }
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setMethodSpelMap(Map<String, List<SpELConfigEntry>> methodSpelMap) {
        METHOD_SPEL_MAP.putAll(methodSpelMap);
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void setPlaceHolder(String placeHolder) {
        this.placeHolder = placeHolder;
    }

    @ToString
    public static class SpELConfigEntry implements Map.Entry<String, List<String>> {

        private String key;

        private List<String> value;

        public SpELConfigEntry(String key, List<String> value) {
            this.key = key;
            this.value = value;
        }

        public static List<SpELConfigEntry> parseEntriesFromJsonArray(JSONArray array) {
            List<SpELConfigEntry> entries = new ArrayList<>(array.size());
            for (Object jsonEntry : array) {
                String key = null;
                List<String> value = null;

                if (jsonEntry instanceof String) {
                    key = (String)jsonEntry;
                } else if (jsonEntry instanceof JSONObject) {
                    JSONObject jsonObject = (JSONObject)jsonEntry;
                    Preconditions.checkArgument(jsonObject.size() == 1);

                    Entry<String, Object> next = jsonObject.entrySet().iterator().next();
                    key = next.getKey();
                    value = ((JSONArray)next.getValue()).stream().map(Object::toString).collect(Collectors.toList());
                }

                entries.add(new SpELConfigEntry(key, value));
            }

            return entries;
        }

        public boolean isMulti() {
            return this.value != null;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public List<String> getValue() {
            return this.value;
        }

        @Override
        public List<String> setValue(List<String> value) {
            return this.value = value;
        }
    }
}
