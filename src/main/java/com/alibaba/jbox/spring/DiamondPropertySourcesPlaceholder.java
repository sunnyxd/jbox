package com.alibaba.jbox.spring;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.taobao.diamond.client.Diamond;
import com.taobao.diamond.manager.ManagerListener;
import com.taobao.diamond.manager.ManagerListenerAdapter;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
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
import java.util.concurrent.CountDownLatch;


/**
 * @author jifang.zjf
 * @since 2017/4/5 上午10:35.
 * <p/>
 * 从Spring 3.1开始建议使用PropertySourcesPlaceholderConfigurer装配properties,
 * 因为它能够基于Spring Environment及其属性源来解析占位符.
 */
public class DiamondPropertySourcesPlaceholder
        extends PropertySourcesPlaceholderConfigurer
        implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(DiamondPropertySourcesPlaceholder.class);

    private static final ConcurrentMap<Class, Class> primitiveTypes = new ConcurrentHashMap<Class, Class>(14) {
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

    private static Set<Object> beanNotInSpring = new HashSet<>();

    private static ConfigurableListableBeanFactory beanFactory;

    private ManagerListener listener;

    private Map<String, String> diamondPropertiesTmp = new HashMap<>();

    private Multimap<String, Pair<Field, Object>> beanAnnotatedByValue = HashMultimap.create();

    private List<Resource> yamlResources = new ArrayList<>();

    /*  -------------------------------------   */
    /*  ----------- Config Data -------------   */
    /*  -------------------------------------   */
    private boolean needDiamond = true;

    private String dataId = "properties";

    private String group = "config";

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
            CountDownLatch latch = new CountDownLatch(1);
            Diamond.addListener(this.dataId, this.group, (this.listener = new DiamondManagerListener(latch)));
            latch.await();
        }
    }

    private class DiamondManagerListener extends ManagerListenerAdapter {

        private volatile boolean isInit = true;

        private CountDownLatch latch;

        private DiamondManagerListener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            JSONObject propertyJson = JSONObject.parseObject(configInfo);
            if (isInit) {
                initDiamondProperties(propertyJson);
                isInit = false;
            } else {
                handleDataChanged(propertyJson);
            }
            latch.countDown();
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

            yamlMap.forEach(properties::put);
        }

        return properties;
    }

    private void saveProperties(Map<?, ?> map) {
        if (!isNullOrEmpty(map)) {
            map.forEach(wholeProperties::put);
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

                Collection<Pair<Field, Object>> filedWithBeans = this.beanAnnotatedByValue.get(key);
                if (!isNullOrEmpty(filedWithBeans)) {
                    for (Pair<Field, Object> pair : filedWithBeans) {
                        Field field = pair.getLeft();
                        Object beanInstance = pair.getRight();
                        Object filedValue = convertTypeValue(currentValue, field.getType(), field.getGenericType());

                        try {
                            field.set(beanInstance, filedValue);
                        } catch (IllegalAccessException ignored) {
                            // 不可能发生
                        }

                        logger.warn("class: {}`s instance field: {} threshold is change to {}", beanInstance.getClass().getName(),
                                field.getName(), filedValue);
                    }
                } else {
                    logger.error("propertyKey: {} have not found relation bean, threshold: {}", key, currentValue);
                }
            }
        }
    }

    private void initBeansMap(ConfigurableListableBeanFactory beanFactory) {
        if (beanAnnotatedByValue.isEmpty()) {
            // 将被Spring托管的Bean放入beanAnnotatedByValue管理
            String[] beanNames = beanFactory.getBeanDefinitionNames();
            for (String beanName : beanNames) {
                Object bean = beanFactory.getBean(beanName);
                initBeanMap(getAOPTarget(bean));
            }

            // 将Spring外的Bean也放入beanAnnotatedByValue管理
            beanNotInSpring.forEach((bean) -> initBeanMap(getAOPTarget(bean)));
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
        makeAccessible(field);

        String value = field.getAnnotation(Value.class).value();
        if (value.startsWith("${") && value.endsWith("}")) {
            return value.substring("${".length(), value.length() - 1);
        } else {
            throw new RuntimeException("@Value annotation need \"${[config key]}\" config in the threshold() property");
        }
    }

    // 仅支持八种基本类型和String
    public static <T> Object convertTypeValue(String value, Class<T> type, Type genericType) {
        Object instance = null;
        Class<?> primitiveType = primitiveTypes.get(type);
        if (primitiveType != null) {
            try {
                instance = primitiveType.getMethod("valueOf", String.class).invoke(null, value);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            }
        } else if (type == Character.class || type == char.class) {
            instance = value.charAt(0);
        } else if (type == String.class) {
            return value;
        } else {
            instance = JSON.parseObject(value, genericType);
        }

        return instance;
    }

    /**
     * From spring-core
     * Make the given field accessible, explicitly setting it accessible if
     * necessary. The {@code setAccessible(true)} method is only called
     * when actually necessary, to avoid unnecessary conflicts with a JVM
     * SecurityManager (if active).
     *
     * @param field the field to make accessible
     * @see Field#setAccessible
     */
    private void makeAccessible(Field field) {
        if ((!Modifier.isPublic(field.getModifiers()) || !Modifier.isPublic(field.getDeclaringClass().getModifiers()) ||
                Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
            field.setAccessible(true);
        }
    }

    private boolean isNullOrEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    private boolean isNullOrEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    private Object getAOPTarget(Object bean) {
        if (AopUtils.isAopProxy(bean)) {
            try {
                if (AopUtils.isJdkDynamicProxy(bean)) {
                    bean = getJDKProxyTarget(bean);
                } else {
                    bean = getCglibProxyTarget(bean);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return bean;
    }

    private Object getJDKProxyTarget(Object proxy) throws Exception {
        Field h = proxy.getClass().getSuperclass().getDeclaredField("h");
        h.setAccessible(true);
        AopProxy aopProxy = (AopProxy) h.get(proxy);

        Field advised = aopProxy.getClass().getDeclaredField("advised");
        advised.setAccessible(true);

        return ((AdvisedSupport) advised.get(aopProxy)).getTargetSource().getTarget();
    }

    private Object getCglibProxyTarget(Object proxy) throws Exception {
        Field h = proxy.getClass().getDeclaredField("CGLIB$CALLBACK_0");
        makeAccessible(h);
        Object dynamicAdvisedInterceptor = h.get(proxy);

        Field advised = dynamicAdvisedInterceptor.getClass().getDeclaredField("advised");
        advised.setAccessible(true);

        return ((AdvisedSupport) advised.get(dynamicAdvisedInterceptor)).getTargetSource().getTarget();
    }

    @Override
    public void destroy() throws Exception {
        if (this.listener != null) {
            Diamond.removeListener(this.dataId, this.group, this.listener);
        }
    }
}
