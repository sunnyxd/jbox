package com.vdian.jbox.domain;

/**
 * @author jifang
 * @since 2017/3/1 下午2:41.
 */
public class People {
    private String name;
    private Address address;

    public People(String name, Address address) {
        this.address = address;
        this.name = name;
    }
}
