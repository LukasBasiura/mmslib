package com.klinker.android.send_message;

/**
 * MMS APN configuration for a specific carrier / SIM.
 */
public final class MmsApn {

    private final String name;
    private final String mmsc;
    private final String proxy;
    private final String port;
    private final int subscriptionId;

    public MmsApn(String name, String mmsc, String proxy, String port, int subscriptionId) {
        this.name = name != null ? name : "";
        this.mmsc = mmsc != null ? mmsc : "";
        this.proxy = proxy != null ? proxy : "";
        this.port = port != null ? port : "";
        this.subscriptionId = subscriptionId;
    }

    public String getName() {
        return name;
    }

    public String getMmsc() {
        return mmsc;
    }

    public String getProxy() {
        return proxy;
    }

    public String getPort() {
        return port;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public boolean isValid() {
        return mmsc != null && !mmsc.trim().isEmpty();
    }

    @Override
    public String toString() {
        return name.isEmpty() ? mmsc : name;
    }
}