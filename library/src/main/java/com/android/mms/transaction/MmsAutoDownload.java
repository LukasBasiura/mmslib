package com.android.mms.transaction;

import android.app.Service;
import android.content.Context;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;

/**
 * MMS auto-download preference checks (extracted from legacy {@code NotificationTransaction}).
 */
public final class MmsAutoDownload {

    private MmsAutoDownload() {
    }

    public static boolean allowAutoDownload(Context context) {
        try {
            Looper.prepare();
        } catch (Exception ignored) {
        }
        boolean autoDownload = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("auto_download_mms", true);
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
        boolean dataSuspended = telephonyManager != null
                && telephonyManager.getDataState() == TelephonyManager.DATA_SUSPENDED;
        return autoDownload && !dataSuspended;
    }
}