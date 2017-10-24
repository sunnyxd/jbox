package com.alibaba.jbox.trace

import com.google.common.base.Strings
import groovy.util.slurpersupport.GPathResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.alibaba.jbox.trace.ELResolveHelpers.replaceRefToRealString

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/10/18 18:25:00.
 */
class ELXmlResolver {

    private static final Logger logger = LoggerFactory.getLogger(TLogManager.class)

    static void resolve(String fileName, InputStream xmlIs, Map<String, List<AbstractTLogConfig.ELConfig>> methodELMap) {
        def slurper = new XmlSlurper(false, true)
        GPathResult traces = slurper.parse(xmlIs)

        // 1. read definitions
        Map<String, String> definitions = [:]
        traces.define.collect {
            definitions.put(it.@name.toString(), it.@value.toString())
        }

        logDetailConfigs(fileName, "definitions", definitions)

        // 2. 解析新增config
        Map<String, String> references = [:]
        traces.trace.collect {
            String method = replaceRefToRealString(it.@method.toString(), definitions)
            String ref = replaceRefToRealString(it.@ref.toString(), definitions)

            // ref another config
            if (!Strings.isNullOrEmpty(ref)) {
                references.put(method, ref)
            } else {
                resolveExpressions(method, it.expression, methodELMap)
            }
        }

        // 3. 注入ref config
        references.each { method, ref ->
            def refConfig = methodELMap.computeIfAbsent(ref, { key -> throw new TraceException("relative config '${key}' is not defined.") })
            methodELMap.put(method, refConfig)
        }

        logDetailConfigs(fileName, "methodELMap", methodELMap)
    }

    private static void logDetailConfigs(String fileName, String configName, Map<String, ?> methodELMap) {
        int maxLength = Integer.MIN_VALUE
        methodELMap.each { key, value ->
            if (key.length() > maxLength) {
                maxLength = key.length()
            }
        }
        StringBuilder sb = new StringBuilder()
        Map<String, ?> sortedMap = methodELMap.sort { entry1, entry2 ->
            entry2.key.length() - entry1.key.length()
        }
        sortedMap.each { key, value ->
            sb
                    .append(String.format(" %-${maxLength + 2}s", "'${key}'"))
                    .append(" -> ")
                    .append(value)
                    .append("\n")
        }

        logger.info("read from [{}] {}:\n{}", fileName, configName, sb)
    }

    private static void resolveExpressions(String method, def expressions,
                                           Map<String, List<AbstractTLogConfig.ELConfig>> methodELMap) {
        expressions.collect {
            iterator ->
                String key = iterator.@key
                String multi = iterator.@multi

                def elConfig
                if (!Strings.isNullOrEmpty(method) && Boolean.valueOf(multi)) {     // is multi config
                    elConfig = resolveFields(key, iterator.field)
                } else {                                                            // single config
                    elConfig = new AbstractTLogConfig.ELConfig(key, null)
                }
                methodELMap.computeIfAbsent(method, { k -> [] }).add(elConfig)
        }
    }

    private static AbstractTLogConfig.ELConfig resolveFields(String key, def fields) {
        List<String> inListParamEL = []
        fields.collect {
            inListParamEL.add(it.@value.toString())
        }
        new AbstractTLogConfig.ELConfig(key, inListParamEL)
    }
}
