package com.alibaba.jbox.domain;

/**
 * @author jifang
 * @since 2017/3/1 下午2:40.
 */
public class Address {
    private String local;

    private double lat;

    private double lng;

    public Address() {
    }

    public Address(String local, double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
        this.local = local;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }
}
