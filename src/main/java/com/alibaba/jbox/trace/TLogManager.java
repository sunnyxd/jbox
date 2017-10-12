package com.alibaba.jbox.trace;

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
import com.alibaba.jbox.utils.DateUtils;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.InitializingBean;

import static com.alibaba.jbox.trace.LogBackHelper.initTLogger;
import static com.alibaba.jbox.trace.TraceConstants.SEPARATOR;
import static com.alibaba.jbox.trace.TraceConstants.TLOG_EXECUTOR_GROUP;
import static java.util.stream.Collectors.toList;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.1
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
            tLogger = initTLogger(getUniqueLoggerName(), getFilePath(), getCharset(), getMaxHistory(), getFilters());
        }
    }

    void postLogEvent(LogEvent event) {
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

            logEntities.add(ifNotNull(event.getArgs(), JSONObject::toJSONString));        // nullable
            logEntities.add(ifNotNull(event.getResult(), JSONObject::toJSONString));      // nullable
            logEntities.add(ifNotNull(event.getException(), JSONObject::toJSONString));   // nullable
            logEntities.add(event.getServerIp());
            logEntities.add(event.getTraceId());                                          // nullable
            logEntities.add(event.getClientName());                                       // nullable
            logEntities.add(event.getClientIp());                                         // nullable

            // get config from specified configs map
            List<SpELConfig> spELConfigs = getMethodSpelMap().getOrDefault(
                event.getConfigKey(), Collections.emptyList()
            );

            // check is multi config or not
            Object[] multi = getMultiConfig(spELConfigs);
            int multiIdx = (int)multi[0];
            SpELConfig multiConfig = (SpELConfig)multi[1];
            List<SpELConfig> notMultiSpELConfigs = (List<SpELConfig>)multi[2];

            if (multiIdx == -1) {   // do as single config
                evalSingleConfig(event, logEntities, notMultiSpELConfigs);
            } else {                // do as multi config
                evalMultiConfig(event, logEntities, notMultiSpELConfigs, multiIdx, multiConfig);
            }
        }

        private Object[] getMultiConfig(List<SpELConfig> spELConfigs) {
            List<SpELConfig> notMultiSpELConfigs = new ArrayList<>(spELConfigs.size());

            int multiIdx = -1;
            SpELConfig multiConfig = null;
            for (int i = 0; i < spELConfigs.size(); ++i) {
                SpELConfig config = spELConfigs.get(i);

                if (config.isMulti()) {
                    multiIdx = i;
                    multiConfig = config;
                } else {
                    notMultiSpELConfigs.add(config);
                }
            }

            return new Object[] {multiIdx, multiConfig, notMultiSpELConfigs};
        }

        private void evalSingleConfig(LogEvent logEvent, List<Object> logEntities,
                                      List<SpELConfig> notMultiSpELConfigs) {
            // 非multi的SpELConfig内只有paramEL内有值/有效
            List<String> paramELs = notMultiSpELConfigs.stream().map(SpELConfig::getParamEL).collect(toList());

            // 将根据spel计算的结果与原metadata合并
            List<Object> evalResults = SpELHelpers.evalSpelWithEvent(logEvent, paramELs, getPlaceHolder());
            logEntities.addAll(evalResults);

            doLogger(logEntities);
        }

        private void evalMultiConfig(LogEvent logEvent, List<Object> logEntities, List<SpELConfig> notMultiSpELConfigs,
                                     int multiIdx, SpELConfig multiConfig) {
            // 0) 首先拿非multi的Config计算: 非multi的SpELConfig内只有paramEL内有值/有效
            List<String> notMultiParamELs = notMultiSpELConfigs.stream().map(SpELConfig::getParamEL).collect(toList());
            List<Object> notMultiEvalResults = SpELHelpers.evalSpelWithEvent(logEvent, notMultiParamELs, getPlaceHolder());

            // 1) 根据multiPramEL提取出ListArg
            List<Object> multiArgs = SpELHelpers.evalSpelWithEvent(event,
                Collections.singletonList(multiConfig.getParamEL()), getPlaceHolder());
            Preconditions.checkState(multiArgs.size() == 1);

            List listArg = transMultiArgToList(multiArgs.get(0));

            // 2) 遍历list argument
            for (Object listArgEntry : listArg) {

                // 2.1)
                List<Object> multiEvalResults;
                if (multiConfig.getInListParamEL().isEmpty()) {                 // 没有inner field, 如'List<String>'
                    multiEvalResults = Collections.singletonList(listArgEntry);
                } else {                                                        // 有  inner filed, 如'List<User>'
                    multiEvalResults = SpELHelpers.evalSpelWithObject(listArgEntry, multiConfig.getInListParamEL(),
                        getPlaceHolder());
                }

                // 2.2)
                appendEvalResultsAndLog(notMultiEvalResults, multiEvalResults, logEntities, multiIdx,
                    notMultiSpELConfigs.size() + 1);
            }
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

        private void appendEvalResultsAndLog(List<Object> notMultiEvalResults, List<Object> multiEvalResults,
                                             List<Object> logEntities, int multiIdx, int paramCount) {
            List<Object> logEntitiesCopy = new ArrayList<>(logEntities);
            int notMultiResultIdx = 0;
            for (int paramIdx = 0; paramIdx < paramCount; ++paramIdx) {
                if (paramIdx == multiIdx) {                 // is multi arg
                    logEntitiesCopy.addAll(multiEvalResults);
                } else {
                    logEntitiesCopy.add(notMultiEvalResults.get(notMultiResultIdx));
                    notMultiResultIdx++;
                }
            }

            doLogger(logEntitiesCopy);
        }

        private void doLogger(List<Object> logEntities) {
            String content = Joiner.on(SEPARATOR).useForNull(getPlaceHolder()).join(logEntities);
            tLogger.trace("{}", content);
        }

        @Override
        public String taskInfo() {
            return MessageFormatter.format("{}: [{}]", this.getClass().getSimpleName(), event.getConfigKey())
                .getMessage();
        }
    }

    private static String ifNotNull(Object nullableObj, Function<Object, String> processor) {
        return nullableObj == null ? null : processor.apply(nullableObj);
    }
}
