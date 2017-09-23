package com.alibaba.jbox.trace;

import java.nio.charset.Charset;

import com.alibaba.jbox.script.ScriptExecutor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
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

    static void initTLogger(Logger logger, String filePath, String charset) {
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
                rolling.setMaxHistory(15);
                rolling.setContext(tLogger.getLoggerContext());
                rolling.start();
                appender.setRollingPolicy(rolling);

                PatternLayoutEncoder layout = new PatternLayoutEncoder();
                layout.setPattern("[%thread]%m%n");
                layout.setCharset(Charset.forName(charset));
                layout.setContext(tLogger.getLoggerContext());
                layout.start();
                appender.setEncoder(layout);
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
}
