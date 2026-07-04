package com.klinker.android.send_message;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.google.android.mms.util_alt.SqliteWrapper;
import com.klinker.android.logger.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads and persists MMS APN data without any UI.
 * MessageMate (or any host app) should use this for SIM-specific APN selection.
 */
public final class MmsApnResolver {

    private static final String TAG = "MmsApnResolver";

    private static final String PREF_MMSC = "mmsc_url_sub_";
    private static final String PREF_PROXY = "mms_proxy_sub_";
    private static final String PREF_PORT = "mms_port_sub_";

    private MmsApnResolver() {
    }

    public static List<MmsApn> loadAvailableApns(Context context, int subscriptionId) {
        final int subId = Utils.resolveSubscriptionId(subscriptionId);
        final List<MmsApn> fromSystem = loadFromSystemDatabase(context, subId);
        if (!fromSystem.isEmpty()) {
            return fromSystem;
        }
        final List<MmsApn> fromXml = loadFromBundledXml(context, subId);
        if (!fromXml.isEmpty()) {
            return fromXml;
        }
        final MmsApn saved = getSavedApn(context, subId);
        if (saved != null && saved.isValid()) {
            return Collections.singletonList(saved);
        }
        return Collections.emptyList();
    }

    public static MmsApn loadPreferredApn(Context context, int subscriptionId) {
        final int subId = Utils.resolveSubscriptionId(subscriptionId);
        final MmsApn saved = getSavedApn(context, subId);
        if (saved != null && saved.isValid()) {
            return saved;
        }
        final List<MmsApn> available = loadAvailableApns(context, subId);
        if (available.size() == 1) {
            return available.get(0);
        }
        return null;
    }

    public static MmsApn getSavedApn(Context context, int subscriptionId) {
        final int subId = Utils.resolveSubscriptionId(subscriptionId);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String mmsc = prefs.getString(PREF_MMSC + subId, "");
        if (mmsc == null || mmsc.trim().isEmpty()) {
            return null;
        }
        return new MmsApn(
                "",
                mmsc,
                prefs.getString(PREF_PROXY + subId, ""),
                prefs.getString(PREF_PORT + subId, ""),
                subId);
    }

    public static void saveSelectedApn(Context context, int subscriptionId, MmsApn apn) {
        final int subId = Utils.resolveSubscriptionId(subscriptionId);
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_MMSC + subId, apn.getMmsc())
                .putString(PREF_PROXY + subId, apn.getProxy())
                .putString(PREF_PORT + subId, apn.getPort())
                .apply();
    }

    public static Settings applyToSettings(Settings settings, MmsApn apn) {
        if (apn == null) {
            return settings;
        }
        settings.setMmsc(apn.getMmsc());
        settings.setProxy(apn.getProxy());
        settings.setPort(apn.getPort());
        if (apn.getSubscriptionId() != Settings.DEFAULT_SUBSCRIPTION_ID) {
            settings.setSubscriptionId(apn.getSubscriptionId());
        }
        return settings;
    }

    public static Settings createSettingsForSim(Context context, int subscriptionId, MmsApn apn) {
        final Settings settings = Utils.getDefaultSendSettings(context);
        settings.setSubscriptionId(subscriptionId);
        if (apn != null) {
            applyToSettings(settings, apn);
        } else {
            final MmsApn preferred = loadPreferredApn(context, subscriptionId);
            if (preferred != null) {
                applyToSettings(settings, preferred);
            }
        }
        return settings;
    }

    private static List<MmsApn> loadFromSystemDatabase(Context context, int subId) {
        final List<MmsApn> apns = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = SqliteWrapper.query(
                    context,
                    context.getContentResolver(),
                    android.net.Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "/subId/" + subId),
                    new String[]{
                            Telephony.Carriers.TYPE,
                            Telephony.Carriers.MMSC,
                            Telephony.Carriers.MMSPROXY,
                            Telephony.Carriers.MMSPORT,
                            Telephony.Carriers.NAME,
                    },
                    null,
                    null,
                    null);
            if (cursor == null) {
                return apns;
            }
            while (cursor.moveToNext()) {
                final String type = cursor.getString(0);
                if (!isValidApnType(type, "mms")) {
                    continue;
                }
                final String mmsc = trim(cursor.getString(1));
                if (mmsc.isEmpty()) {
                    continue;
                }
                final String proxy = trim(cursor.getString(2));
                final String port = trim(cursor.getString(3));
                final String name = trim(cursor.getString(4));
                final MmsApn candidate = new MmsApn(name, mmsc, proxy, port, subId);
                if (!containsApn(apns, candidate)) {
                    apns.add(candidate);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "failed loading APNs from system database", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return apns;
    }

    private static List<MmsApn> loadFromBundledXml(Context context, int subId) {
        final int[] mccMnc = resolveMccMnc(context, subId);
        if (mccMnc[0] < 0 || mccMnc[1] < 0) {
            return Collections.emptyList();
        }
        final int mcc = mccMnc[0];
        final int mnc = mccMnc[1];

        final List<MmsApn> apns = new ArrayList<>();
        XmlResourceParser parser = context.getResources().getXml(R.xml.apns);
        String mmsc = "";
        String proxy = "";
        String port = "";
        String carrier = "";

        try {
            beginDocument(parser, "apns");
            while (true) {
                nextElement(parser);
                final String tag = parser.getName();
                if (tag == null) {
                    break;
                }

                boolean mccCorrect = false;
                boolean mncCorrect = false;
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    final String name = parser.getAttributeName(i);
                    try {
                        final int value = Integer.parseInt(parser.getAttributeValue(i));
                        if ("mcc".equals(name) && mcc == value) {
                            mccCorrect = true;
                        } else if ("mnc".equals(name) && mnc == value) {
                            mncCorrect = true;
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (!mccCorrect || !mncCorrect) {
                    continue;
                }

                mmsc = "";
                proxy = "";
                port = "";
                carrier = "";
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    final String name = parser.getAttributeName(i);
                    final String value = parser.getAttributeValue(i);
                    if ("type".equals(name)) {
                        if (!value.contains("mms")) {
                            mmsc = "";
                            break;
                        }
                    } else if ("mmsc".equals(name)) {
                        mmsc = value;
                    } else if ("mmsproxy".equals(name)) {
                        proxy = value;
                    } else if ("mmsport".equals(name)) {
                        port = value;
                    } else if ("carrier".equals(name)) {
                        carrier = value;
                    } else if ("port".equals(name) && port.isEmpty()) {
                        port = value;
                    }
                }

                if (!mmsc.isEmpty()) {
                    final MmsApn candidate = new MmsApn(carrier, mmsc, proxy, port, subId);
                    if (!containsApn(apns, candidate)) {
                        apns.add(candidate);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "failed loading APNs from bundled xml", e);
        } finally {
            parser.close();
        }
        return apns;
    }

    private static int[] resolveMccMnc(Context context, int subId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
            final SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfo(subId);
            if (info != null) {
                return new int[]{info.getMcc(), info.getMnc()};
            }
        }
        final TelephonyManager manager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        final String networkOperator = manager != null ? manager.getNetworkOperator() : null;
        if (networkOperator != null && networkOperator.length() >= 5) {
            try {
                final int mcc = Integer.parseInt(networkOperator.substring(0, 3));
                final int mnc = Integer.parseInt(networkOperator.substring(3).replaceFirst("^0{1,2}", ""));
                return new int[]{mcc, mnc};
            } catch (Exception ignored) {
            }
        }
        return new int[]{context.getResources().getConfiguration().mcc,
                context.getResources().getConfiguration().mnc};
    }

    private static boolean containsApn(List<MmsApn> apns, MmsApn candidate) {
        for (MmsApn existing : apns) {
            if (existing.getMmsc().equals(candidate.getMmsc())
                    && existing.getProxy().equals(candidate.getProxy())
                    && existing.getPort().equals(candidate.getPort())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidApnType(String types, String requestType) {
        if (types == null || types.trim().isEmpty()) {
            return true;
        }
        for (String type : types.split(",")) {
            type = type.trim();
            if (type.equals(requestType) || type.equals("*")) {
                return true;
            }
        }
        return false;
    }

    private static String trim(String value) {
        return value != null ? value.trim() : "";
    }

    private static void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != parser.START_TAG && type != parser.END_DOCUMENT) {
        }
        if (type != parser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }
        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName()
                    + ", expected " + firstElementName);
        }
    }

    private static void nextElement(XmlPullParser parser) throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != parser.START_TAG && type != parser.END_DOCUMENT) {
        }
    }
}