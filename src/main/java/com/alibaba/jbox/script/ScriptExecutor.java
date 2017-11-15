package com.alibaba.jbox.script;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import com.alibaba.jbox.spring.AbstractApplicationContextAware;

import com.google.common.base.Strings;
import com.taobao.hsf.app.spring.util.HSFSpringProviderBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.stereotype.Service;

import static com.alibaba.jbox.script.ScriptType.Python;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.1
 * @since jbox-1.4.0 (16/10/9 下午6:05).
 */
@Service("com.alibaba.jbox.script.ScriptService")
public class ScriptExecutor extends AbstractApplicationContextAware
    implements IScriptExecutor, BeanDefinitionRegistryPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger("com.alibaba.jbox.script");

    private static final String HSF_SERVICE_NAME = "com.alibaba.hsf.script.ScriptExecutor";

    private static final String HACKER_SALT = "@$^_^$@";

    private static final Map<String, Object> contextNotInSpring = new HashMap<>();

    private Reference<Bindings> reference = new SoftReference<>(null);

    private String salt;

    private String version = "1.0.0";

    private String location = "filter.properties";

    private int waitTimeout = 1000 * 30;

    @Override
    public Map<String, String> context() throws ScriptException {
        try {
            Properties properties = new Properties();
            properties.load(this.getClass().getClassLoader().getResourceAsStream(location));

            Bindings bindings = loadScriptContext();
            Map<String, String> contexts = new LinkedHashMap<>();
            contexts.put("${bean name}", "${bean type}");

            return bindings.entrySet()
                .stream()
                .filter(entry -> {
                    String prop = properties.getProperty(entry.getKey());
                    return prop == null || !"name".equals(prop);
                })
                .filter(entry -> {
                    String className = entry.getValue().getClass().getName();
                    String prop = properties.getProperty(className);
                    return prop == null || !"type".equals(prop);
                })
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().getClass().getName(),
                    (m1, m2) -> m1,
                    () -> contexts));

        } catch (IOException e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Object execute(String script, ScriptType type, String salt) throws ScriptException {
        try {
            if (HACKER_SALT.equals(salt) || Objects.equals(salt, this.salt)) {
                Object result;
                if (Python.equals(type)) {
                    result = PythonScriptSupport.invokePythonScript(loadScriptContext(), script);
                } else {
                    ScriptEngineManager manager = new ScriptEngineManager();
                    manager.setBindings(loadScriptContext());

                    ScriptEngine engine = manager.getEngineByName(type.getValue());
                    result = engine.eval(script);
                }

                logger.warn("script: {{}} invoke success, result: {}", script, result);

                return result;
            }
        } catch (Exception e) {
            logger.error("script: {{}} invoke error", script, e);
            throw new ScriptException(e);
        }

        return "your script is not security, salt: "
            + (Strings.isNullOrEmpty(salt) ? "[empty]" : salt);
    }

    @Override
    public String reloadContext() throws ScriptException {
        reference.clear();
        loadScriptContext();
        return "reload script executor context success !";
    }

    @Override
    public void registerContext(String name, Object value) {
        contextNotInSpring.put(name, value);
    }

    public static void register(String name, Object value) {
        contextNotInSpring.put(name, value);
    }

    private Bindings loadScriptContext() {

        Bindings bindings;
        if ((bindings = reference.get()) == null) {
            bindings = new SimpleBindings();

            String[] beanNames = applicationContext.getBeanDefinitionNames();
            for (String beanName : beanNames) {
                bindings.put(beanName, applicationContext.getBean(beanName));
            }
            bindings.putAll(contextNotInSpring);

            reference = new SoftReference<>(bindings);
        }

        return bindings;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!registry.containsBeanDefinition(HSF_SERVICE_NAME)) {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(HSFSpringProviderBean.class);
            beanDefinition.setInitMethodName("init");
            beanDefinition.getPropertyValues().addPropertyValue("serviceInterface", getInterfaceName());
            beanDefinition.getPropertyValues().addPropertyValue("serviceInterfaceClass", IScriptExecutor.class);
            beanDefinition.getPropertyValues().addPropertyValue("serviceName", IScriptExecutor.class.getSimpleName());
            beanDefinition.getPropertyValues().addPropertyValue("serviceVersion", version);
            beanDefinition.getPropertyValues().addPropertyValue("target", this);
            beanDefinition.getPropertyValues().addPropertyValue("clientTimeout", waitTimeout);

            registry.registerBeanDefinition(HSF_SERVICE_NAME, beanDefinition);
            logger.warn("service [{}] register success.", HSF_SERVICE_NAME);
        }
    }

    private String getInterfaceName() {
        return String.format("ScriptExecutor[%s]", System.getProperty("project.name", "unknown"));
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setWaitTimeout(int waitTimeout) {
        this.waitTimeout = waitTimeout;
    }
}
