package com.alibaba.jbox.trace;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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
import com.alibaba.jbox.utils.JboxUtils;

import com.ali.com.google.common.base.Strings;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.CharStreams;
import lombok.Data;
import lombok.ToString;
import org.springframework.core.io.Resource;

import static com.alibaba.jbox.trace.TraceConstants.DEFAULT_MAX_HISTORY;
import static com.alibaba.jbox.trace.TraceConstants.DEFAULT_RUNNABLE_Q_SIZE;
import static com.alibaba.jbox.trace.TraceConstants.DEF_PREFIX;
import static com.alibaba.jbox.trace.TraceConstants.DEF_SUFFIX;
import static com.alibaba.jbox.trace.TraceConstants.LOG_SUFFIX;
import static com.alibaba.jbox.trace.TraceConstants.MAX_THREAD_POOL_SIZE;
import static com.alibaba.jbox.trace.TraceConstants.MIN_THREAD_POOL_SIZE;
import static com.alibaba.jbox.trace.TraceConstants.PLACEHOLDER;
import static com.alibaba.jbox.trace.TraceConstants.REF_PREFIX;
import static com.alibaba.jbox.trace.TraceConstants.REF_SUFFIX;
import static com.alibaba.jbox.trace.TraceConstants.USER_DEF;
import static com.alibaba.jbox.trace.TraceConstants.UTF_8;

/**
 * TLogManager统一配置
 *
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.2
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
        String json = readConfig(spelResource);

        Map<String, Object> jsonObject = JSONObject.parseObject(json, Feature.AutoCloseSource);
        // 先将用户自定义def解析出来
        Map<String, String> defs = parseUserDefs(jsonObject);

        // 支持config乱序引用
        Map<String, String> refs = new HashMap<>();
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            // 替换为最终key
            String key = replaceRefToValue(entry.getKey(), defs);

            List<SpELConfigEntry> spels = methodSpelMap.computeIfAbsent(key, k -> new LinkedList<>());
            Object value = entry.getValue();
            if (value instanceof String) {                                  // 引用的其他配置
                String refConfig = parseRefConfigValue(value, defs);
                refs.put(key, refConfig);
            } else if (value instanceof JSONArray) {                        // 新创建的配置
                List<SpELConfigEntry> newConfigs = SpELConfigEntry.parseEntriesFromJsonArray((JSONArray)value);
                spels.addAll(newConfigs);

                // @since 1.2支持methodName缩写引用
                String methodName = Splitter.on(":").splitToList(key).get(1);
                methodSpelMap.put(methodName, spels);
            } else {                                                        // 错误的配置语法
                throw new TraceException(
                    "your config '" + value
                        + "' is not relative defined config or not a new config. please check your config syntax is "
                        + "correct or mail to jifang.zjf@alibaba-inc.com.");
            }
        }

        // 将引用的配置注册进去
        for (Map.Entry<String, String> entry : refs.entrySet()) {
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

    private String readConfig(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream();
             Reader reader = new InputStreamReader(is)) {
            return CharStreams.toString(reader);
        }
    }

    private Map<String, String> parseUserDefs(Map<String, Object> jsonObject) {
        Map<String, String> defs = new HashMap<>();
        List<String> needRemoveKeys = new LinkedList<>();
        for (Entry<String, Object> entry : jsonObject.entrySet()) {
            String key = entry.getKey();

            if (!key.startsWith(USER_DEF) || !(entry.getValue() instanceof String)) {
                continue;
            }

            String defKey = key.substring(USER_DEF.length()).trim();
            defKey = JboxUtils.trimPrefixAndSuffix(defKey, DEF_PREFIX, DEF_SUFFIX);
            defs.put(defKey, (String)entry.getValue());

            needRemoveKeys.add(key);
        }

        for (String key : needRemoveKeys) {
            jsonObject.remove(key);
        }

        return defs;
    }

    private String replaceRefToValue(String key, Map<String, String> defs) {
        int prefixIdx = key.indexOf(DEF_PREFIX);
        int suffixIdx = key.indexOf(DEF_SUFFIX);

        if (prefixIdx != -1 && suffixIdx != -1) {
            String ref = key.substring(prefixIdx, suffixIdx + DEF_SUFFIX.length());
            String trimmedRef = JboxUtils.trimPrefixAndSuffix(ref, DEF_PREFIX, DEF_SUFFIX);
            String refValue = defs.computeIfAbsent(trimmedRef, (k) -> {
                throw new TraceException("relative def '" + ref + "' is not defined.");
            });

            key = key.replace(ref, refValue);
        }

        return key;
    }

    private String parseRefConfigValue(Object entryValue, Map<String, String> defs) {
        String strValue = replaceRefToValue((String)entryValue, defs);
        if (strValue.startsWith(REF_PREFIX) && strValue.endsWith(REF_SUFFIX)) {
            String configRef = JboxUtils.trimPrefixAndSuffix(strValue, DEF_PREFIX, DEF_SUFFIX, false);
            return configRef;
        } else {
            throw new TraceException(
                "your config '" + entryValue
                    + "' is not relative defined config or not a new config. please check your config syntax is "
                    + "correct or mail to jifang.zjf@alibaba-inc.com.");
        }
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
