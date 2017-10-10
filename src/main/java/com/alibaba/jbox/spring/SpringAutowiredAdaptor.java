package com.alibaba.jbox.spring;

import java.lang.reflect.Field;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ReflectionUtils;

import static com.alibaba.jbox.utils.JboxUtils.trimPrefixAndSuffix;

/**
 * 使非Spring托管的Bean可以使用`@Autowired`、`@Resource`、`@Value`注解
 * 同时`@Value`注解可以享受与普通SpringBean同样的特权, 即: 可以使用基于Diamond的配置, 动态生效
 *
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/6/7 下午8:28.
 */
public abstract class SpringAutowiredAdaptor {

    public void initAutowired() {
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(Autowired.class)
                || field.isAnnotationPresent(Resource.class)
                || field.isAnnotationPresent(Value.class)) {

                Value value;
                Qualifier qualifier;

                Object beanValue;
                if ((value = field.getAnnotation(Value.class)) != null) {
                    // 将该对象以"Spring外的Bean"的方式注册到Placeholder管理器中
                    // 以使其具备动态修改@Value属性的能力
                    DiamondPropertySourcesPlaceholder.registerSpringOuterBean(this);

                    Object property = DiamondPropertySourcesPlaceholder.getProperty(
                        trimPrefixAndSuffix(value.value(), "${", "}"));
                    if (property == null) {
                        throw new RuntimeException("could not found the config " + value.value() + " value");
                    }
                    beanValue = DiamondPropertySourcesPlaceholder.convertTypeValue((String)property, field.getType(),
                        field.getGenericType());
                } else if ((qualifier = field.getAnnotation(Qualifier.class)) != null) {
                    beanValue = DiamondPropertySourcesPlaceholder.getSpringBean(qualifier.value());
                } else {
                    beanValue = DiamondPropertySourcesPlaceholder.getSpringBean(field.getType());
                }

                ReflectionUtils.makeAccessible(field);
                try {
                    field.set(this, beanValue);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }
}
