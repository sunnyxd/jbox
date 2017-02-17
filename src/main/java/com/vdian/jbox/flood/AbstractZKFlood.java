package com.vdian.jbox.flood;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * @author jifang
 * @since 2017/2/16 下午6:01.
 */
public abstract class AbstractZKFlood extends AbFloodExperiment {

    private static final String FLOOD_NAMESPACE = "flood";
    private volatile double rate;

    private volatile Object inValue;

    private volatile Object outValue;

    public AbstractZKFlood(String connectString) throws Exception {
        this(connectString, -1);
    }

    public AbstractZKFlood(String connectString, int sessionTimeoutMs) throws Exception {
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .namespace(FLOOD_NAMESPACE);
        if (sessionTimeoutMs != -1) {
            builder.sessionTimeoutMs(sessionTimeoutMs);
        }

        CuratorFramework client = builder.build();
        client.start();

        byte[] bytes = client.getData().forPath(nodeName());
        updateFloodValue(bytes);
        final NodeCache nodeCache = new NodeCache(client, nodeName());
        nodeCache.start();
        nodeCache.getListenable().addListener(new NodeCacheListener() {
            @Override
            public void nodeChanged() throws Exception {
                byte[] data = nodeCache.getCurrentData().getData();
                updateFloodValue(data);
            }
        });
    }

    protected abstract String nodeName();

    protected String rateKey() {
        return "rate";
    }

    protected String inKey() {
        return "in";
    }

    protected String outKey() {
        return "out";
    }

    @Override
    protected double getRate() {
        return this.rate;
    }

    @Override
    protected Object getInValue() {
        return this.inValue;
    }

    @Override
    protected Object getOutValue() {
        return this.outValue;
    }

    private void updateFloodValue(byte[] bytes) {
        JSONObject json = JSON.parseObject(new String(bytes));
        String rate = json.getString(rateKey());

        int i = rate.lastIndexOf("%");
        if (i != -1) {
            rate = rate.substring(0, i);
        }
        this.rate = Double.valueOf(rate);

        this.inValue = json.get(inKey());
        this.outValue = json.get(outKey());

        FLOOD_LOGGER.info("update flood value, rate = {}%, in value = {}, out value = {}", this.rate, this.inValue, this.outValue);
    }
}
