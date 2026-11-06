package com.dnsorder.wififix;

import android.net.LinkProperties;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Moves IPv6 DNS servers behind IPv4 ones on Wi-Fi networks.
 *
 * Hooks DnsManager.noteDnsServersForNetwork(int, LinkProperties) in the connectivity
 * mainline jar (separate classloader, relocated package on Android 16) and hands the
 * resolver a reordered COPY of the LinkProperties. ConnectivityService's own copy is
 * left untouched so its "DNS changed?" comparison keeps working.
 *
 * Debug: adb logcat -s WifiDnsOrder
 */
public class DnsOrderModule extends XposedModule {

    private static final String TAG = "WifiDnsOrder";

    private static final String SSM = "com.android.server.SystemServiceManager";
    private static final String SYSTEM_SERVICE = "com.android.server.SystemService";
    private static final String CS_INITIALIZER = "com.android.server.ConnectivityServiceInitializer";
    private static final String DNS_MANAGER =
            "android.net.connectivity.com.android.server.connectivity.DnsManager";

    private final AtomicBoolean dnsHooked = new AtomicBoolean(false);

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        Log.i(TAG, "module loaded in system_server");
        ClassLoader systemLoader = param.getClassLoader();

        // Some ROMs may keep the connectivity classes in the normal loader.
        tryHookDns(systemLoader, "system_server loader");

        // The connectivity jar is a standalone jar with its own classloader. Catch it when
        // SystemServiceManager starts ConnectivityServiceInitializer.
        try {
            Class<?> ssm = systemLoader.loadClass(SSM);
            Class<?> systemService = systemLoader.loadClass(SYSTEM_SERVICE);
            Method startService = ssm.getDeclaredMethod("startService", systemService);
            hook(startService).intercept(chain -> {
                try {
                    Object service = chain.getArg(0);
                    if (service != null && service.getClass().getName().equals(CS_INITIALIZER)) {
                        Log.i(TAG, "ConnectivityServiceInitializer is starting");
                        tryHookDns(service.getClass().getClassLoader(), "connectivity jar loader");
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "error while capturing connectivity classloader", t);
                }
                return chain.proceed();
            });
            Log.i(TAG, "hooked SystemServiceManager.startService");
        } catch (Throwable t) {
            Log.e(TAG, "failed to hook SystemServiceManager.startService", t);
        }
    }

    private void tryHookDns(ClassLoader loader, String origin) {
        if (loader == null || dnsHooked.get()) {
            return;
        }
        Class<?> dnsManager;
        try {
            dnsManager = loader.loadClass(DNS_MANAGER);
        } catch (ClassNotFoundException e) {
            Log.i(TAG, "DnsManager not found in " + origin);
            return;
        }
        if (!dnsHooked.compareAndSet(false, true)) {
            return;
        }
        try {
            Method target = dnsManager.getDeclaredMethod(
                    "noteDnsServersForNetwork", int.class, LinkProperties.class);
            hook(target).intercept(this::onNoteDnsServers);
            Log.i(TAG, "hooked DnsManager.noteDnsServersForNetwork (via " + origin + ")");
        } catch (Throwable t) {
            dnsHooked.set(false);
            Log.e(TAG, "failed to hook DnsManager.noteDnsServersForNetwork", t);
        }
    }

    private Object onNoteDnsServers(XposedInterface.Chain chain) throws Throwable {
        Object[] newArgs = null;
        try {
            Object[] args = chain.getArgs().toArray();
            int netId = (Integer) args[0];
            LinkProperties lp = (LinkProperties) args[1];
            LinkProperties reordered = reorder(netId, lp);
            if (reordered != null) {
                args[1] = reordered;
                newArgs = args;
            }
        } catch (Throwable t) {
            Log.e(TAG, "reorder failed, passing through unchanged", t);
        }
        return newArgs != null ? chain.proceed(newArgs) : chain.proceed();
    }

    /** Returns LinkProperties with IPv4 DNS first, or null if no change is needed. */
    private LinkProperties reorder(int netId, LinkProperties lp) {
        if (lp == null) {
            return null;
        }
        String iface = lp.getInterfaceName();
        if (iface == null || !iface.startsWith("wlan")) {
            return null; // Wi-Fi only
        }

        List<InetAddress> original = lp.getDnsServers();
        List<InetAddress> v4 = new ArrayList<>();
        List<InetAddress> v6 = new ArrayList<>();
        for (InetAddress a : original) {
            if (a instanceof Inet4Address) {
                v4.add(a);
            } else {
                v6.add(a);
            }
        }
        if (v4.isEmpty() || v6.isEmpty()) {
            return null;
        }
        List<InetAddress> ordered = new ArrayList<>(v4);
        ordered.addAll(v6);
        if (ordered.equals(original)) {
            return null;
        }

        LinkProperties copy = copyOf(lp);
        if (copy == null) {
            // Fallback: mutate in place. Works, but ConnectivityService will see a DNS
            // change on every update and reconfigure the resolver each time.
            Log.w(TAG, "copy constructor unavailable, reordering in place");
            copy = lp;
        }
        copy.setDnsServers(ordered);
        Log.i(TAG, "net " + netId + " (" + iface + ") DNS " + original + " -> " + ordered);
        return copy;
    }

    private static LinkProperties copyOf(LinkProperties lp) {
        try {
            Constructor<LinkProperties> c = LinkProperties.class.getConstructor(LinkProperties.class);
            return c.newInstance(lp);
        } catch (Throwable t) {
            Log.w(TAG, "LinkProperties copy failed: " + t);
            return null;
        }
    }
}
