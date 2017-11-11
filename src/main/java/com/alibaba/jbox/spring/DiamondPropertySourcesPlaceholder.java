package com.alibaba.jbox.spring;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import com.alibaba.jbox.executor.AsyncRunnable;
import com.alibaba.jbox.spring.ValueHandler.ValueContext;
import com.alibaba.jbox.utils.AopTargetUtils;
import com.alibaba.jbox.utils.JboxUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.taobao.diamond.client.Diamond;
import com.taobao.diamond.manager.ManagerListener;
import com.taobao.diamond.manager.ManagerListenerAdapter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.util.ReflectionUtils;

import static com.alibaba.jbox.utils.JboxUtils.convertTypeValue;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.4
 * - 1.0: a simple vitamin faced use {@link PlaceholderConfigurerSupport};
 * - 1.1: replace use {@link PropertySourcesPlaceholderConfigurer}, 从3.1版本起Spring其装配.
 * - 1.2: use {@link Diamond} for replace Vitamin;
 * - 1.3 @deprecated : load yaml file as a config file format;
 * - 1.4: add {@link ValueHandler} when Field value changed.
 * @since 2017/4/5 上午10:35.
 */
public class DiamondPropertySourcesPlaceholder extends PropertySourcesPlaceholderConfigurer
    implements InitializingBean, DisposableBean {

    private static final String SEP_LINE = "\n";

    private static final String SEP_KV = ":";

    private static Multimap<String, Pair<Field, Object>> configKey2PairMap = HashMultimap.create();

    private static Properties totalProperties = new Properties();

    private static Set<Object> externalBeans = new HashSet<>();

    private static ConfigurableListableBeanFactory beanFactory;

    private Map<String, String> initDiamondConfig;

    private ManagerListener listener;

    /*  -------------------------------------   */
    /*  ----------- Diamond Config ----------   */
    /*  -------------------------------------   */
    @Setter
    private boolean needDiamond = false;

    @Setter
    private String dataId = "properties";

    @Setter
    private String groupId = "config";

    @Setter
    private long timeoutMs = 5 * 1000;

    /*  -------------------------------------   */
    /*  --------------- APIs ----------------   */

    /*  -------------------------------------   */
    public static BeanFactory getBeanFactory() {
        return beanFactory;
    }

    public static Object getSpringBean(String id) {
        return beanFactory.getBean(id);
    }

    public static <T> T getSpringBean(Class<T> type) {
        return beanFactory.getBean(type);
    }

    public static void registerExternalBean(Object bean) {
        externalBeans.add(bean);
    }

    public static Properties getProperties() {
        return totalProperties;
    }

    public static Object getProperty(Object key) {
        return totalProperties.get(key);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.needDiamond) {
            Preconditions.checkState(!Strings.isNullOrEmpty(this.groupId), "diamond 'groupId' can not be empty.");
            Preconditions.checkState(!Strings.isNullOrEmpty(this.dataId), "diamond 'dataId' can not be empty.");

            String config = Diamond.getConfig(this.dataId, this.groupId, this.timeoutMs);
            if (!Strings.isNullOrEmpty(config)) {
                initDiamondConfig = parseConfig(config);
            }
            Diamond.addListener(this.dataId, this.groupId, this.listener = new IgnoreSameConfigListener(config));
        }
    }

    private class IgnoreSameConfigListener extends ManagerListenerAdapter {

        private volatile String preConfig;

        IgnoreSameConfigListener(String config) {
            this.preConfig = config;
        }

        @Override
        public void receiveConfigInfo(String config) {
            try {
                Map<String, String> configMap = parseConfig(config);
                SpringLoggerHelper.info("receive diamond config data: {}", configMap);
                if (!StringUtils.equals(preConfig, config)) {
                    // handle current config  changed
                    handleDiamondChanged(configMap);

                    // save as pre config
                    preConfig = config;
                }
            } catch (Exception e) {
                SpringLoggerHelper.error("handle diamond config data: {{}} error", config, e);
            }
        }
    }

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess,
                                     ConfigurablePropertyResolver propertyResolver) throws BeansException {
        beanFactory = beanFactoryToProcess;
        super.processProperties(beanFactoryToProcess, propertyResolver);
    }

    @Override
    protected Properties mergeProperties() throws IOException {
        Properties mergedProperties = super.mergeProperties();
        if (!isNullOrEmpty(initDiamondConfig)) {
            mergedProperties.putAll(initDiamondConfig);
        }
        totalProperties.putAll(mergedProperties);
        return mergedProperties;
    }

    /*  -------------------------------------   */
    /*  ------- Handle Data Changes ---------   */
    /*  -------------------------------------   */

    private void handleDiamondChanged(Map<String, String> config) throws Exception {
        initBeansMap(beanFactory);

        Multimap<Object, Pair<Field, Object>> bean2ChangedFieldMap = HashMultimap.create();

        for (Map.Entry<String, String> entry : config.entrySet()) {
            String configKey = entry.getKey();
            String configValue = entry.getValue();

            // make sure is changed.
            if (configValue.equals(totalProperties.get(configKey))) {
                SpringLoggerHelper.warn("passed: config [{}]'s value [{}] is equals current value '{}'.",
                    configKey,
                    configValue,
                    totalProperties.get(configKey));
                continue;
            }

            // make sure has relative bean.
            Collection<Pair<Field, Object>> fieldWithBeans = configKey2PairMap.get(configKey);
            if (isNullOrEmpty(fieldWithBeans)) {
                SpringLoggerHelper.error("passed: config [{}] have none relative bean.", configKey);
                continue;
            }

            // update field changed.
            for (Pair<Field, Object> pair : fieldWithBeans) {
                Field field = pair.getLeft();
                Object value = convertTypeValue(configValue, field.getType(), field.getGenericType());
                Object bean = pair.getRight();
                // set field value
                field.set(bean, value);
                SpringLoggerHelper.warn("success: class '{}' field: '{}' value is update to [{}]",
                    bean.getClass().getName(),
                    field.getName(), value);

                // since 1.4 : add changed field, after notify field changed
                bean2ChangedFieldMap.put(bean, Pair.of(field, value));
            }

            // update saved properties.
            totalProperties.put(configKey, configValue);
        }

        notifyChangedCallBack(bean2ChangedFieldMap.asMap());
    }

    private void notifyChangedCallBack(Map<Object, Collection<Pair<Field, Object>>> bean2ChangedFieldMap) {
        bean2ChangedFieldMap.forEach((bean, pairs) -> {
            if (bean instanceof ValueHandler) {
                ValueHandler handler = (ValueHandler)bean;

                List<Field> changedFields = new ArrayList<>(pairs.size());
                List<Object> changedValues = new ArrayList<>(pairs.size());
                for (Pair<Field, Object> pair : pairs) {
                    changedFields.add(pair.getLeft());
                    changedValues.add(pair.getRight());
                }

                ValueContext context = new ValueContext(changedFields, changedValues);
                AsyncRunnable runnable = new AsyncRunnable() {
                    @Override
                    public void execute() {
                        handler.handle(context);
                        SpringLoggerHelper.warn("ValueHandler: '{}' has been triggered.",
                            bean.getClass().getName());
                    }

                    @Override
                    public String taskInfo() {
                        return MessageFormatter.format("{} waiting trigger class: {}'s fields:{} changed.",
                            new Object[] {
                                this.getClass().getName(),
                                bean.getClass().getName(),
                                context.getChangeFields().stream().map(Field::getName).collect(Collectors.toList())
                            }).getMessage();
                    }
                };

                if (handler.executor() == null) {
                    runnable.execute();
                } else {
                    handler.executor().execute(runnable);
                }
            }
        });
    }

    private void initBeansMap(ConfigurableListableBeanFactory beanFactory) {
        if (configKey2PairMap.isEmpty()) {
            // register spring container bean
            String[] beanNames = beanFactory.getBeanDefinitionNames();
            for (String beanName : beanNames) {
                Object bean = beanFactory.getBean(beanName);
                initBeanMap(AopTargetUtils.getAopTarget(bean));
            }

            // register spring external bean
            for (Object bean : externalBeans) {
                initBeanMap(AopTargetUtils.getAopTarget(bean));
            }
        }
    }

    private void initBeanMap(Object bean) {
        if (bean != null) {
            List<Field> fields = FieldUtils.getFieldsListWithAnnotation(bean.getClass(), Value.class);
            for (Field field : fields) {
                String annotationConfigKey = getValueAnnotationValue(field);
                Pair<Field, Object> pair = Pair.of(field, bean);

                configKey2PairMap.put(annotationConfigKey, pair);
            }
        }
    }

    private String getValueAnnotationValue(Field field) {
        ReflectionUtils.makeAccessible(field);
        String value = field.getAnnotation(Value.class).value();
        value = value.trim();
        Preconditions.checkState(!Strings.isNullOrEmpty(value), "@value() config can not be empty.");
        return JboxUtils.trimPrefixAndSuffix(value, "${", "}");
    }

    @Override
    public void destroy() throws Exception {
        if (this.listener != null) {
            Diamond.removeListener(this.dataId, this.groupId, this.listener);
        }
    }

    /* ------ helpers ----- */
    private Map<String, String> parseConfig(String config) throws IOException {
        List<String> lines = Splitter.on(SEP_LINE).trimResults().omitEmptyStrings().splitToList(config);
        Map<String, String> configMap = new HashMap<>();
        if (!isNullOrEmpty(lines)) {
            for (String line : lines) {
                List<String> kv = Splitter.on(SEP_KV).trimResults().omitEmptyStrings().splitToList(
                    line);

                if (kv.size() != 2) {
                    String msg = MessageFormatter.arrayFormat(
                        "Diamond [{}:{}] config [{}] is not standard 'key:value' property format.",
                        new Object[] {this.groupId, this.dataId, line}).getMessage();
                    throw new PropertySourcesPlaceholderException(msg);
                }

                configMap.put(kv.get(0), kv.get(1));
            }
        }

        return configMap;
    }

    private static boolean isNullOrEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    private static boolean isNullOrEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    private static class PropertySourcesPlaceholderException extends RuntimeException {

        private static final long serialVersionUID = 3949739146397637634L;

        public PropertySourcesPlaceholderException(String message) {
            super(message);
        }

        public PropertySourcesPlaceholderException(Throwable cause) {
            super(cause);
        }

        protected PropertySourcesPlaceholderException(String message, Throwable cause, boolean enableSuppression,
                                                      boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }

        public PropertySourcesPlaceholderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
