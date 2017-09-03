package com.alibaba.jbox.spring;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.jbox.utils.AopTargetUtils;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.taobao.diamond.client.Diamond;
import com.taobao.diamond.manager.ManagerListener;
import com.taobao.diamond.manager.ManagerListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.io.Resource;
import org.springframework.util.ReflectionUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.4
 *          - 1.0: a simple vitamin faced use @{code org.springframework.beans.factory.config.PlaceholderConfigurerSupport};
 *          - 1.1: replace use {@code org.springframework.context.support.PropertySourcesPlaceholderConfigurer};
 *          - 1.2: change vitamin to Diamond;
 *          - 1.3: load yaml file as a config file format;
 *          - 1.4: add {@code com.alibaba.jbox.spring.FieldChangedCallback} when Field value changed.
 * @since 2017/4/5 上午10:35.
 * - 从 spring 3.1 起建议使用'PropertySourcesPlaceholderConfigurer'装配,
 * - 因为它能够基于Environment及其属性源来解析占位符.
 */
public class DiamondPropertySourcesPlaceholder
        extends PropertySourcesPlaceholderConfigurer
        implements InitializingBean, DisposableBean, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(DiamondPropertySourcesPlaceholder.class);

    private static final ConcurrentMap<Class, Class> primitiveTypes = new ConcurrentHashMap<Class, Class>() {
        private static final long serialVersionUID = -4085587013134835589L;

        {
            put(byte.class, Byte.class);
            put(Byte.class, Byte.class);
            put(short.class, Short.class);
            put(Short.class, Short.class);
            put(int.class, Integer.class);
            put(Integer.class, Integer.class);
            put(long.class, Long.class);
            put(Long.class, Long.class);
            put(float.class, Float.class);
            put(Float.class, Float.class);
            put(double.class, Double.class);
            put(Double.class, Double.class);
            put(boolean.class, Boolean.class);
            put(Boolean.class, Boolean.class);
        }
    };

    private static Properties wholeProperties = new Properties();

    private static Set<Object> beanNotInSpring = new HashSet<Object>();

    private static ConfigurableListableBeanFactory beanFactory;

    private static ApplicationContext applicationContext;

    private ManagerListener listener;

    private Map<String, String> diamondPropertiesTmp = new HashMap<String, String>();

    private Multimap<String, Pair<Field, Object>> beanAnnotatedByValue = HashMultimap.create();

    private List<Resource> yamlResources = new ArrayList<Resource>();

    /*  -------------------------------------   */
    /*  ----------- Config Data -------------   */
    /*  -------------------------------------   */
    private boolean needDiamond = true;

    private String dataId = "properties";

    private String group = "config";

    private long timeoutMs = 5 * 1000;

    public void setNeedDiamond(boolean needDiamond) {
        this.needDiamond = needDiamond;
    }

    public void setDataId(String dataId) {
        if (!Strings.isNullOrEmpty(dataId)) {
            this.dataId = dataId;
        }
    }

    public void setGroup(String group) {
        if (!Strings.isNullOrEmpty(group)) {
            this.group = group;
        }
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /*  -------------------------------------   */
    /*  ----------- Public APIs -------------   */
    /*  -------------------------------------   */
    public static Properties getProperties() {
        return wholeProperties;
    }

    public static Object getProperty(Object key) {
        return wholeProperties.get(key);
    }

    public static Object getSpringBean(String id) {
        return beanFactory.getBean(id);
    }

    public static Object getSpringBean(Class<?> type) {
        return beanFactory.getBean(type);
    }

    public static BeanFactory getBeanFactory() {
        return beanFactory;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static void registerSpringOuterBean(Object bean) {
        beanNotInSpring.add(bean);
    }

    // 过滤 & 暂存 yaml 资源
    @Override
    public void setLocation(Resource location) {
        if (!location.getFilename().endsWith(".yaml")) {
            super.setLocation(location);
        } else {
            this.yamlResources.add(location);
        }
    }

    @Override
    public void setLocations(Resource... locations) {
        int length = 0;
        Resource[] newLocations = new Resource[locations.length];
        for (Resource location : locations) {
            if (location.getFilename().endsWith(".yaml")) {
                this.yamlResources.add(location);
            } else {
                newLocations[length++] = location;
            }
        }

        super.setLocations(Arrays.copyOf(newLocations, length));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.needDiamond) {
            String initConfig = Diamond.getConfig(this.dataId, this.group, this.timeoutMs);
            if (!Strings.isNullOrEmpty(initConfig)) {
                initDiamondProperties(JSONObject.parseObject(initConfig));
            }
            Diamond.addListener(this.dataId, this.group, (this.listener = new IgnoreSameConfigListener(initConfig)));
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        DiamondPropertySourcesPlaceholder.applicationContext = applicationContext;
    }

    private class IgnoreSameConfigListener extends ManagerListenerAdapter {

        private volatile String lastContent;

        public IgnoreSameConfigListener(String lastContent) {
            this.lastContent = lastContent;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            if (!StringUtils.equals(lastContent, configInfo)) {
                lastContent = configInfo;
                JSONObject propertyJson = JSONObject.parseObject(configInfo);
                handleDataChanged(propertyJson);
            }
        }
    }

    private void initDiamondProperties(JSONObject propertyJson) {
        for (String propertyKey : propertyJson.keySet()) {
            String propertyValue = propertyJson.getString(propertyKey);

            diamondPropertiesTmp.put(propertyKey, propertyValue);
        }
    }

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, ConfigurablePropertyResolver propertyResolver) throws BeansException {
        // store BeanFactory temporary
        beanFactory = beanFactoryToProcess;
        super.processProperties(beanFactoryToProcess, propertyResolver);
    }

    @Override
    protected Properties mergeProperties() throws IOException {
        Properties fileProperties = super.mergeProperties();
        Properties yamlProperties = this.loadYamlResources();

        // save 3 个数据源:
        // 1. .properties & .xml
        saveProperties(fileProperties);
        // 2. .yaml
        saveProperties(yamlProperties);
        // 3. diamond
        saveProperties(this.diamondPropertiesTmp);

        fileProperties.putAll(yamlProperties);
        fileProperties.putAll(this.diamondPropertiesTmp);

        return fileProperties;
    }

    private Properties loadYamlResources() throws IOException {
        Properties properties = new Properties();
        for (Resource yamlResource : this.yamlResources) {
            /**
             * attention!!! : not support raw array yaml config
             */
            Map<?, ?> yamlMap = (Map<?, ?>) new Yaml().load(yamlResource.getInputStream());

            properties.putAll(yamlMap);
            // yamlMap.forEach(properties::put);
        }

        return properties;
    }

    private void saveProperties(Map<?, ?> map) {
        if (!isNullOrEmpty(map)) {
            wholeProperties.putAll(map);
            // map.forEach(wholeProperties::put);
        }
    }

    /*  -------------------------------------   */
    /*  ------- Handle Data Changes ---------   */
    /*  -------------------------------------   */

    private String convertEntryValue(Object value) {
        String stringValue;
        if (value instanceof String) {
            stringValue = (String) value;
        } else {
            stringValue = String.valueOf(value);
        }

        return stringValue;
    }

    private void handleDataChanged(JSONObject currentJsonConfig) {
        initBeansMap(beanFactory);
        for (Map.Entry<String, Object> entry : currentJsonConfig.entrySet()) {

            String key = entry.getKey();
            String currentValue = convertEntryValue(entry.getValue());

            // make sure is changed
            if (!currentValue.equals(wholeProperties.get(key))) {
                wholeProperties.put(key, currentValue);

                Collection<Pair<Field, Object>> fieldWithBeans = this.beanAnnotatedByValue.get(key);
                List<Pair<Field, Object>> fieldWithValues = new ArrayList<Pair<Field, Object>>(fieldWithBeans.size());
                if (!isNullOrEmpty(fieldWithBeans)) {
                    for (Pair<Field, Object> pair : fieldWithBeans) {
                        Field field = pair.getLeft();
                        Object beanInstance = pair.getRight();
                        Object fieldValue = convertTypeValue(currentValue, field.getType(), field.getGenericType());
                        fieldWithValues.add(Pair.of(field, fieldValue));

                        try {
                            field.set(beanInstance, fieldValue);
                        } catch (IllegalAccessException ignored) {
                            // 不可能发生
                        }

                        logger.warn("class: {}`s instance field: {} threshold is change to {}", beanInstance.getClass().getName(),
                                field.getName(), fieldValue);
                    }
                    notifyCallback(fieldWithValues, fieldWithBeans.iterator().next().getRight());
                } else {
                    logger.error("propertyKey: {} have not found relation bean, threshold: {}", key, currentValue);
                }
            }
        }
    }

    // @since 1.4
    private void notifyCallback(List<Pair<Field, Object>> fieldWithValues, Object target) {
        if (target instanceof FieldChangedCallback) {
            FieldChangedCallback callback = (FieldChangedCallback) target;
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    callback.receiveConfigInfo(fieldWithValues);
                }
            };
            if (callback.getExecutor() == null) {
                runnable.run();
            } else {
                callback.getExecutor().execute(runnable);
            }
        }
    }

    private void initBeansMap(ConfigurableListableBeanFactory beanFactory) {
        if (beanAnnotatedByValue.isEmpty()) {
            try {
                // 将被Spring托管的Bean放入beanAnnotatedByValue管理
                String[] beanNames = beanFactory.getBeanDefinitionNames();
                for (String beanName : beanNames) {
                    Object bean = beanFactory.getBean(beanName);
                    initBeanMap(AopTargetUtils.getTarget(bean));
                }

                // 将Spring外的Bean也放入beanAnnotatedByValue管理
                for (Object bean : beanNotInSpring) {
                    initBeanMap(AopTargetUtils.getTarget(bean));
                }
            } catch (Exception e) {
                logger.error("initBeansMap error", e);
            }
        }
    }

    private void initBeanMap(Object beanInstance) {
        Field[] fields = beanInstance.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Value.class)) {
                String valueMapKey = getValueAnnotationValue(field);
                Pair<Field, Object> pair = Pair.of(field, beanInstance);

                beanAnnotatedByValue.put(valueMapKey, pair);
            }
        }
    }

    private String getValueAnnotationValue(Field field) {
        ReflectionUtils.makeAccessible(field);

        String value = field.getAnnotation(Value.class).value();
        if (value.startsWith("${") && value.endsWith("}")) {
            return value.substring("${".length(), value.length() - 1);
        } else {
            throw new PropertySourcesPlaceholderException("@Value annotation need \"${[config key]}\" config in the threshold() property");
        }
    }

    public static <T> Object convertTypeValue(String value, Class<T> type, Type genericType) {
        Object instance = null;
        Class<?> primitiveType = primitiveTypes.get(type);
        if (primitiveType != null) {
            try {
                instance = primitiveType.getMethod("valueOf", String.class).invoke(null, value);
            } catch (IllegalAccessException ignored) {
            } catch (InvocationTargetException ignored) {
            } catch (NoSuchMethodException ignored) {
            }
        } else if (type == Character.class || type == char.class) {
            instance = value.charAt(0);
        } else if (type == String.class) {
            instance = value;
        } else {
            instance = JSON.parseObject(value, genericType);
        }

        return instance;
    }

    private boolean isNullOrEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    private boolean isNullOrEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    @Override
    public void destroy() throws Exception {
        if (this.listener != null) {
            Diamond.removeListener(this.dataId, this.group, this.listener);
        }
    }

    private static class PropertySourcesPlaceholderException extends RuntimeException {

        public PropertySourcesPlaceholderException(String message) {
            super(message);
        }

        public PropertySourcesPlaceholderException(Throwable cause) {
            super(cause);
        }

        protected PropertySourcesPlaceholderException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }

        public PropertySourcesPlaceholderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
