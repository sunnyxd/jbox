package com.vdian.jbox.annotation;

import java.lang.annotation.*;

/**
 * @author jifang
 * @since 2017/3/1 上午10:44.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface NotEmpty {
}
