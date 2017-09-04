package com.alibaba.jbox.utils;

import com.google.common.base.Preconditions;
import lombok.NonNull;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.function.Supplier;

/**
 * @author jifang
 * @since 16/8/18 下午6:09.
 */
public class JboxUtils {

    private static final Logger logger = LoggerFactory.getLogger("com.alibaba.jbox");

    public static final Object EMPTY = new Object();

    public static Object getFieldValue(@NonNull Object target, @NonNull String filedName) {
        Field field = ReflectionUtils.findField(target.getClass(), filedName);
        ReflectionUtils.makeAccessible(field);
        return ReflectionUtils.getField(field, target);
    }

    public static Object getFieldValue(@NonNull Object target, @NonNull String outerFieldName, String... innerFieldNames) {
        Object outerObject = getFieldValue(target, outerFieldName);

        Object innerObject = null;
        for (String innerFieldName : innerFieldNames) {
            Preconditions.checkNotNull(outerObject);
            Field innerField = ReflectionUtils.findField(outerObject.getClass(), innerFieldName);
            ReflectionUtils.makeAccessible(innerField);
            innerObject = ReflectionUtils.getField(innerField, outerObject);

            outerObject = innerObject;
        }
        return innerObject;
    }

    public static Method getAbstractMethod(JoinPoint pjp) {
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Method method = ms.getMethod();
        return method;
    }

    public static Method getImplMethod(JoinPoint pjp) throws NoSuchMethodException {
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Method method = ms.getMethod();
        if (method.getDeclaringClass().isInterface()) {
            method = pjp.getTarget().getClass().getDeclaredMethod(ms.getName(), method.getParameterTypes());
        }
        return method;
    }

    public static String getStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace != null && stackTrace.length > 2) {
            StringBuilder sb = new StringBuilder("current thread [")
                    .append(Thread.currentThread().getName())
                    .append("]'s stack trace: ");

            for (int i = 2 /*trim Thread.getStackTrace() & JboxUtils.getStackTrace() */; i < stackTrace.length; ++i) {
                sb.append("\n\t").append(stackTrace[i]);
            }

            return sb.toString();
        }

        return "";
    }

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
