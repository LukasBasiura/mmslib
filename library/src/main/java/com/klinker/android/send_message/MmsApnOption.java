package com.klinker.android.send_message;

/**
 * One selectable MMS configuration row for a host-app dialog.
 * Display strings here are technical carrier data from the system, not UI copy.
 * The host app should provide translated titles, buttons and helper text.
 */
public final class MmsApnOption {

    private final int index;
    private final MmsApn apn;

    public MmsApnOption(int index, MmsApn apn) {
        this.index = index;
        this.apn = apn;
    }

    public int getIndex() {
        return index;
    }

    public MmsApn getApn() {
        return apn;
    }

    /**
     * Primary label for a list row. Uses carrier name when available, otherwise MMSC URL.
     */
    public String getPrimaryLabel() {
        if (apn.getName() != null && !apn.getName().trim().isEmpty()) {
            return apn.getName().trim();
        }
        return apn.getMmsc();
    }

    /**
     * Secondary technical details the host app may optionally show under the primary label.
     */
    public String getTechnicalSummary() {
        final StringBuilder summary = new StringBuilder();
        summary.append(apn.getMmsc());
        if (apn.getProxy() != null && !apn.getProxy().trim().isEmpty()) {
            summary.append(" | ").append(apn.getProxy());
            if (apn.getPort() != null && !apn.getPort().trim().isEmpty()) {
                summary.append(':').append(apn.getPort());
            }
        }
        return summary.toString();
    }
}