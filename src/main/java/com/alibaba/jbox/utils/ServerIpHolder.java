package com.alibaba.jbox.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.function.Supplier;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @since 2017/7/22 14:31.
 */
public class ServerIpHolder {

    private static final Logger logger = LoggerFactory.getLogger(ServerIpHolder.class);

    public static String getServerIp() {
        return serverIpSupplier.get();
    }

    private static final Supplier<String> serverIpSupplier = () -> {
        String serverIp = null;
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            boolean fonded = false;
            while (!fonded && networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet4Address
                            && !inetAddress.isLoopbackAddress()
                            && !inetAddress.isSiteLocalAddress()) {

                        serverIp = inetAddress.getHostAddress();
                        fonded = true;
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            logger.error("get local host ip error", e);
        }

        return serverIp;
    };
}
