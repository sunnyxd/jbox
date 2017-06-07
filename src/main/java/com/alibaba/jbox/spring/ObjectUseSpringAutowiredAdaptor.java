package com.alibaba.jbox.spring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ReflectionUtils;

import javax.annotation.Resource;
import java.lang.reflect.Field;

/**
 * 似非Spring托管的Bean可以使用@Autowired、@Resource、@Value注解
 * 同时@Value注解可以享受与普通SpringBean同样的特权, 即: 可以使用基于Diamond的配置, 动态生效
 *
 * @author jifang.zjf
 * @since 2017/6/7 下午8:28.
 */
public interface ObjectUseSpringAutowiredAdaptor {

    default void initAutowiredValue() {
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Autowired.class)
                    || field.isAnnotationPresent(Resource.class)
                    || field.isAnnotationPresent(Value.class)) {

                Value value;
                Qualifier qualifier;

                Object bean;
                if (null != (value = field.getAnnotation(Value.class))) {
                    // 将该对象以Spring外Bean的方式注册到Placeholder管理器中, 以备动态修改@Value属性
                    DiamondPropertySourcesPlaceholder.registerSpringOuterBean(this);

                    Object property = DiamondPropertySourcesPlaceholder.getProperty(trimPrefixAndSuffix(value.value()));
                    if (property == null) {
                        throw new RuntimeException("could not found the config " + value.value() + " value");
                    }
                    bean = DiamondPropertySourcesPlaceholder.convertTypeValue((String) property, field.getType());
                } else if (null != (qualifier = field.getAnnotation(Qualifier.class))) {
                    bean = DiamondPropertySourcesPlaceholder.getSpringBean(qualifier.value());
                } else {
                    bean = DiamondPropertySourcesPlaceholder.getSpringBean(field.getType());
                }

                ReflectionUtils.makeAccessible(field);
                try {
                    field.set(this, bean);
                } catch (IllegalAccessException e) {
                    // no case
                }
            }
        }
    }

    default String trimPrefixAndSuffix(String value) {
        if (value.startsWith("${")) {
            value = value.substring("${".length());
        }
        if (value.endsWith("}")) {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }
}
