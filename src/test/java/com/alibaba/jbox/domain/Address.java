package com.alibaba.jbox.domain;

/**
 * @author jifang
 * @since 2017/3/1 下午2:40.
 */
public class Address {
    private String local;

    private double lat;

    private double lng;

    public Address(String local, double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
        this.local = local;
    }
}
