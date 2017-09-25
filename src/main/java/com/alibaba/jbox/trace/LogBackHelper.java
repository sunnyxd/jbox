package com.alibaba.jbox.trace;

import java.nio.charset.Charset;
import java.util.List;

import com.alibaba.jbox.script.ScriptExecutor;
import com.alibaba.jbox.trace.TLogManager.TLogEventParser;
import com.alibaba.jbox.trace.TLogManager.TLogFilter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.spi.FilterReply;
import com.ali.com.google.common.base.Strings;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;

import static com.alibaba.jbox.trace.TraceAspect.traceLogger;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/23 08:15:00.
 */
class LogBackHelper {

    static void initTLogger(Logger logger, String filePath, String charset, int maxHistory, List<TLogFilter> filters) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(filePath), "log file can't be empty!");

        try {
            Class.forName("ch.qos.logback.classic.Logger");
            if (logger instanceof ch.qos.logback.classic.Logger) {
                ch.qos.logback.classic.Logger tLogger = (ch.qos.logback.classic.Logger)logger;
                RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
                appender.setName(tLogger.getName());
                appender.setContext(tLogger.getLoggerContext());
                appender.setFile(filePath);
                appender.setAppend(true);

                TimeBasedRollingPolicy rolling = new TimeBasedRollingPolicy();
                rolling.setParent(appender);
                rolling.setFileNamePattern(filePath + ".%d{yyyy-MM-dd}");
                rolling.setMaxHistory(maxHistory);
                rolling.setContext(tLogger.getLoggerContext());
                rolling.start();
                appender.setRollingPolicy(rolling);

                PatternLayoutEncoder layout = new PatternLayoutEncoder();
                layout.setPattern("%m%n%n");
                layout.setCharset(Charset.forName(charset));
                layout.setContext(tLogger.getLoggerContext());
                layout.start();
                appender.setEncoder(layout);

                registerBuildInFilter(appender);
                registerFilters(appender, filters);
                appender.start();

                tLogger.detachAndStopAllAppenders();
                tLogger.setAdditive(false);
                tLogger.setLevel(Level.ALL);
                tLogger.addAppender(appender);
                ScriptExecutor.register("logbackAppender", appender);
            } else {
                traceLogger.warn("application not used Logback implementation,"
                    + " please config 'com.alibaba.jbox.trace.TLogManager' logger in your application manual.");
            }
        } catch (ClassNotFoundException e) {
            traceLogger.warn(
                "class 'ch.qos.logback.classic.Logger' not found(application not used Logback implementation),"
                    + " please config 'com.alibaba.jbox.trace.TLogManager' logger in your application manual.");
        }
    }

    private static void registerBuildInFilter(RollingFileAppender<ILoggingEvent> appender) {
        appender.addFilter(new Filter<ILoggingEvent>() {
            @Override
            public FilterReply decide(ILoggingEvent event) {
                StackTraceElement[] callerData = event.getCallerData();
                if (callerData != null && callerData.length >= 1) {
                    if (TLogEventParser.class.getName().equals(callerData[0].getClassName())) {
                        return FilterReply.NEUTRAL;
                    } else {
                        return FilterReply.DENY;
                    }
                }

                return FilterReply.NEUTRAL;
            }
        });
    }

    private static void registerFilters(RollingFileAppender<ILoggingEvent> appender, List<TLogFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (TLogFilter filter : filters) {
            // 将com.alibaba.jbox.trace.TLogManager.TLogFilter转换为ch.qos.logback.core.filter.Filter, 注册到Appender上
            appender.addFilter(new Filter<ILoggingEvent>() {
                @Override
                public FilterReply decide(ILoggingEvent event) {
                    try {
                        TLogFilter.FilterReply reply = filter.decide(event.getFormattedMessage());
                        return FilterReply.valueOf(reply.name());
                    } catch (Exception e) {
                        traceLogger.error("filter '{}' invoke error, formatted message: {}",
                            filter, event.getFormattedMessage(), e);
                        return FilterReply.DENY;
                    }
                }
            });
        }
    }
}
