package com.alibaba.jbox.trace;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;

import com.ali.com.google.common.base.Strings;
import com.google.common.base.Preconditions;
import com.google.common.io.CharStreams;
import lombok.Data;
import lombok.ToString;
import org.springframework.core.io.Resource;

import static com.alibaba.jbox.trace.TraceConstants.DEFAULT_MAX_HISTORY;
import static com.alibaba.jbox.trace.TraceConstants.DEFAULT_RUNNABLE_Q_SIZE;
import static com.alibaba.jbox.trace.TraceConstants.LOG_SUFFIX;
import static com.alibaba.jbox.trace.TraceConstants.MAX_THREAD_POOL_SIZE;
import static com.alibaba.jbox.trace.TraceConstants.MIN_THREAD_POOL_SIZE;
import static com.alibaba.jbox.trace.TraceConstants.PLACEHOLDER;
import static com.alibaba.jbox.trace.TraceConstants.UTF_8;

/**
 * TLogManager统一配置
 *
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/26 14:09:00.
 */
@Data
public abstract class AbstractTLogConfig implements Serializable {

    private static final long serialVersionUID = 5924881023295492855L;

    private ConcurrentMap<String, List<SpELConfigEntry>> methodSpelMap = new ConcurrentHashMap<>();

    private int minPoolSize = MIN_THREAD_POOL_SIZE;

    private int maxPoolSize = MAX_THREAD_POOL_SIZE;

    private int runnableQSize = DEFAULT_RUNNABLE_Q_SIZE;

    private int maxHistory = DEFAULT_MAX_HISTORY;

    private String placeHolder = PLACEHOLDER;

    /**
     * 为防止日志打串, 最好为每一个logger、appender定义一个独立的name
     * 默认为filePath
     */
    private String uniqueLoggerName;

    private List<TLogFilter> filters;

    private String charset = UTF_8;

    private boolean sync = false;

    private String filePath;

    public void setSpelResource(Resource spelResource) throws IOException {
        String json = CharStreams.toString(new InputStreamReader(spelResource.getInputStream()));

        // 支持乱序引用config
        Map<String, String> relativeConfig = new HashMap<>();

        Map<String, Object> jsonObject = JSONObject.parseObject(json, Feature.AutoCloseSource);
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            List<SpELConfigEntry> spels = methodSpelMap.computeIfAbsent(key, k -> new LinkedList<>());
            Object value = entry.getValue();
            // 引用其他的配置
            if (value instanceof String && ((String)value).startsWith("#")) {
                relativeConfig.put(key, ((String)value).substring(1));
            }
            // 新创建的配置
            else if (value instanceof JSONArray) {
                List<SpELConfigEntry> newConfigs = SpELConfigEntry.parseEntriesFromJsonArray((JSONArray)value);
                spels.addAll(newConfigs);
            }
            // 错误的配置语法
            else {
                throw new TraceException(
                    "your config '" + value
                        + "' is not relative defined config or not a new config. please check your config syntax is "
                        + "correct or mail to jifang.zjf@alibaba-inc.com.");
            }
        }

        // 将引用的配置注册进去
        for (Map.Entry<String, String> entry : relativeConfig.entrySet()) {
            List<SpELConfigEntry> definedConfig = methodSpelMap.computeIfAbsent(entry.getValue(),
                (key) -> {
                    throw new TraceException("relative config '" + key + "' is not defined.");
                });

            methodSpelMap.computeIfAbsent(entry.getKey(), key -> new LinkedList<>()).addAll(definedConfig);
        }
    }

    public void setMethodSpelMap(Map<String, List<SpELConfigEntry>> methodSpelMap) {
        this.methodSpelMap.putAll(methodSpelMap);
    }

    public void addFilter(TLogFilter filter) {
        if (this.filters == null) {
            this.filters = new LinkedList<>();
        }
        this.filters.add(filter);
    }

    public String getUniqueLoggerName() {
        if (Strings.isNullOrEmpty(this.uniqueLoggerName)) {
            String loggerName = this.filePath;
            int prefixIndex = loggerName.lastIndexOf(File.separator);
            if (prefixIndex != -1) {
                loggerName = loggerName.substring(prefixIndex + 1);
            }

            int suffixIndex = loggerName.lastIndexOf(LOG_SUFFIX);
            if (suffixIndex != -1) {
                loggerName = loggerName.substring(0, suffixIndex);
            }

            return loggerName;
        } else {
            return this.uniqueLoggerName;
        }
    }

    public interface TLogFilter {

        enum FilterReply {
            DENY,
            NEUTRAL,
            ACCEPT;
        }

        FilterReply decide(String formattedMessage) throws Exception;
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
