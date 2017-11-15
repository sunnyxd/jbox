package com.alibaba.jbox.trace.tlog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.jbox.executor.AsyncRunnable;
import com.alibaba.jbox.executor.ExecutorManager;
import com.alibaba.jbox.executor.policy.DiscardPolicy;
import com.alibaba.jbox.trace.TraceException;
import com.alibaba.jbox.utils.DateUtils;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.InitializingBean;

import static com.alibaba.jbox.trace.tlog.LogBackHelper.initTLogger;
import static com.alibaba.jbox.trace.tlog.TLogConstants.SEPARATOR;
import static com.alibaba.jbox.trace.tlog.TLogConstants.TLOG_EXECUTOR_GROUP;
import static java.util.stream.Collectors.toList;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.2
 * @since 2017/9/22 15:50:00.
 */
public class TLogManager extends AbstractTLogConfig implements InitializingBean {

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

            RejectedExecutionHandler rejected = new DiscardPolicy(TLOG_EXECUTOR_GROUP);

            executor = ExecutorManager.newFixedMinMaxThreadPool(
                TLOG_EXECUTOR_GROUP, getMinPoolSize(), getMaxPoolSize(),
                getRunnableQSize(), rejected);
        }

        // init tLogger
        if (tLogger == null) {
            tLogger = initTLogger(getUniqueLoggerName(), getFilePath(), getCharset(), getMaxHistory(),
                getTotalSizeCapKb(),
                getFilters());
        }
    }

    public void postLogEvent(LogEvent event) {
        executor.submit(new LogEventParser(event));
    }

    protected final class LogEventParser implements AsyncRunnable {

        private LogEvent event;

        LogEventParser(LogEvent event) {
            this.event = event;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void execute() {
            // 用于打印log的元素集合
            List<Object> logEntities = new LinkedList<>();
            logEntities.add(DateUtils.millisFormatFromMillis(event.getStartTime()));
            logEntities.add(event.getInvokeThread());
            logEntities.add(event.getRt());
            logEntities.add(event.getClassName());
            logEntities.add(event.getMethodName());

            logEntities.add(ifNotNull(event.getArgs(),
                (args) -> JSONObject.toJSONString(args, getArgFilter())));                // nullable
            logEntities.add(ifNotNull(event.getResult(),
                (result) -> JSONObject.toJSONString(result, getResultFilter())));         // nullable
            logEntities.add(ifNotNull(event.getException(), JSONObject::toJSONString));   // nullable
            logEntities.add(event.getServerIp());
            logEntities.add(event.getTraceId());                                          // nullable
            logEntities.add(event.getClientName());                                       // nullable
            logEntities.add(event.getClientIp());                                         // nullable

            // get config from specified configs map
            List<ELConfig> configs = getMethodELMap().getOrDefault(
                event.getConfigKey(), Collections.emptyList()
            );

            // check is multi config or not
            Object[] multi = getMultiConfig(configs);
            int multiIdx = (int)multi[0];
            ELConfig multiCfg = (ELConfig)multi[1];
            List<ELConfig> notMultiCfgs = (List<ELConfig>)multi[2];

            if (multiIdx == -1) {   // do as single config
                logAsSingle(event, logEntities, notMultiCfgs);
            } else {                // do as multi config
                logAsMulti(event, logEntities, notMultiCfgs, multiIdx, multiCfg);
            }
        }

        private Object[] getMultiConfig(List<ELConfig> configs) {
            List<ELConfig> notMultiCfgs = new ArrayList<>(configs.size());

            int multiIdx = -1;
            ELConfig multiCfg = null;
            for (int i = 0; i < configs.size(); ++i) {
                ELConfig config = configs.get(i);

                if (config.isMulti()) {
                    multiIdx = i;
                    multiCfg = config;
                } else {
                    notMultiCfgs.add(config);
                }
            }

            return new Object[] {multiIdx, multiCfg, notMultiCfgs};
        }

        private void logAsSingle(LogEvent logEvent, List<Object> logEntities, List<ELConfig> notMultiCfgs) {

            // not-multi-config内只有paramEL有效
            List<String> paramELs = notMultiCfgs.stream().map(ELConfig::getParamEL).collect(toList());

            // append到从TraceAspect默认采集的MetaData后面
            List<Object> evalResults = SpELHelpers.evalSpelWithEvent(logEvent, paramELs, getPlaceHolder());
            logEntities.addAll(evalResults);

            doLogger(logEntities);
        }

        private void logAsMulti(LogEvent logEvent, List<Object> logEntities, List<ELConfig> notMultiELConfigs,
                                int multiIdx, ELConfig multiCfg) {
            // 0. 首先将not-multi的config表达式计算出占位值(not-multi-config内只有paramEL有效)
            List<String> notMultiParamELs = notMultiELConfigs.stream().map(ELConfig::getParamEL).collect(toList());
            List<Object> notMultiEvalResults = SpELHelpers.evalSpelWithEvent(logEvent, notMultiParamELs,
                getPlaceHolder());

            // 1. 将multi指定的参数转换为list
            List listArg = evalMultiValue(logEvent, multiCfg);

            // 2. 遍历list, 将其与not-multi-value & TraceAspect采集到的MetaData合并
            for (Object listArgEntry : listArg) {

                // 2.1)
                List<Object> multiEvalResults;
                if (multiCfg.getInListParamEL().isEmpty()) {                    // 没有field(inner), 如'List<String>'
                    multiEvalResults = Collections.singletonList(listArgEntry);
                } else {                                                        // 有filed(inner), 如'List<User>'
                    multiEvalResults = SpELHelpers.evalSpelWithObject(listArgEntry, multiCfg.getInListParamEL(),
                        getPlaceHolder());
                }

                // 2.2) 将trace-metadata、not-multi-value、multi-value三者合并
                List<Object> logEntitiesCopy = mergeEvalResult(notMultiEvalResults, multiEvalResults, logEntities,
                    multiIdx, notMultiELConfigs.size() + 1);

                doLogger(logEntitiesCopy);
            }
        }

        /**
         * 使用multi-config指定的paramEL对event求值, 并将值转换为List
         *
         * @param logEvent
         * @param multiCfg
         * @return
         */
        private List evalMultiValue(LogEvent logEvent, ELConfig multiCfg) {
            List<Object> multiArgs = SpELHelpers.evalSpelWithEvent(logEvent,
                Collections.singletonList(multiCfg.getParamEL()), getPlaceHolder());
            Preconditions.checkState(multiArgs.size() == 1);

            return transMultiArgToList(multiArgs.get(0));
        }

        @SuppressWarnings("unchecked")
        private List transMultiArgToList(Object multiArg) {
            if (multiArg == null) {
                return Collections.emptyList();
            } else if (multiArg instanceof ArrayList) {
                return (List)multiArg;
            } else if (multiArg instanceof Collections) {
                return new ArrayList((Collection)multiArg);
            } else if (multiArg.getClass().isArray()) {
                return Arrays.stream((Object[])multiArg).collect(toList());
            } else {
                throw new TraceException("argument [" + multiArg + "] neither array nor collection instance.");
            }
        }

        private List<Object> mergeEvalResult(List<Object> notMultiEvalResults, List<Object> multiEvalResults,
                                             List<Object> logEntities, int multiIdx, int paramCount) {
            List<Object> logEntitiesCopy = new ArrayList<>(logEntities);
            for (int paramIdx = 0, notMultiResultIdx = 0; paramIdx < paramCount; ++paramIdx) {
                if (paramIdx == multiIdx) {                 // is multi arg
                    logEntitiesCopy.addAll(multiEvalResults);
                } else {
                    logEntitiesCopy.add(notMultiEvalResults.get(notMultiResultIdx));
                    notMultiResultIdx++;
                }
            }

            return logEntitiesCopy;
        }

        private void doLogger(List<Object> logEntities) {
            String content = Joiner.on(SEPARATOR).useForNull(getPlaceHolder()).join(logEntities);
            tLogger.trace("{}", content);
        }

        @Override
        public String taskInfo() {
            return MessageFormatter.format("{}: [{}]",
                this.getClass().getSimpleName(), event.getMethod().toGenericString())
                .getMessage();
        }
    }

    private String ifNotNull(Object nullableObj, Function<Object, String> processor) {
        return nullableObj == null ? null : processor.apply(nullableObj);
    }
}
