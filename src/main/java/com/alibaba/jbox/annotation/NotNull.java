package com.alibaba.jbox.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author jifang@alibaba-inc.com
 * @version 1.1
 * @since 2017/3/1 上午10:42.
 * @deprecated 从jbox 1.3.1开始替换为javax.validator api
 */
@Deprecated
@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotNull {
    String name() default "";
}
