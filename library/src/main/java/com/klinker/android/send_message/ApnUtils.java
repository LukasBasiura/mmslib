package com.klinker.android.send_message;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.widget.Toast;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated Use {@link MmsApnResolver} for data and host-app UI for selection.
 */
@Deprecated
public class ApnUtils {

    private static final String TAG = "ApnUtils";

    /**
     * @deprecated Use {@link MmsApnResolver#loadAvailableApns(Context, int)} and host-app UI instead.
     */
    @Deprecated
    public static void initDefaultApns(final Context context, final OnApnFinishedListener listener) {
        initDefaultApns(context, Utils.getDefaultSubscriptionId(), listener);
    }

    /**
     * @deprecated Use {@link MmsApnResolver} and host-app UI instead.
     */
    @Deprecated
    public static void initDefaultApns(final Context context, final int subscriptionId,
                                       final OnApnFinishedListener listener) {
        loadMmsSettings(context);
        final ArrayList<APN> apns = toLegacyApns(MmsApnResolver.loadAvailableApns(context, subscriptionId));

        if (apns == null || apns.size() == 0) {
            Log.v(TAG, "Found no APNs :( Damn CDMA network probably.");
            Toast.makeText(context, context.getString(R.string.auto_select_failed), Toast.LENGTH_SHORT).show();
            if (listener != null) {
                listener.onFinished();
            }
        } else if (apns.size() == 1) {
            setApns(context, apns.get(0));
            if (listener != null) {
                listener.onFinished();
            }
        } else {
            if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("has_seen_select_apns_warning", false)) {
                new AlertDialog.Builder(context)
                        .setTitle(R.string.auto_select_apn)
                        .setMessage(R.string.auto_select_multiple_apns)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int num) {
                                showApnChooser(context, apns, listener);
                            }
                        })
                        .show();
                PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("has_seen_select_apns_warning", true).commit();
            } else {
                showApnChooser(context, apns, listener);
            }
        }
    }

    private static void showApnChooser(final Context context, final ArrayList<APN> apns, final OnApnFinishedListener listener) {
        CharSequence[] items = new CharSequence[apns.size()];
        final MmsApn saved = MmsApnResolver.getSavedApn(context, Utils.getDefaultSubscriptionId());
        String curMmsc = saved != null ? saved.getMmsc() : "";
        String curProxy = saved != null ? saved.getProxy() : "";
        String curPort = saved != null ? saved.getPort() : "";

        int defaultApn = -1;
        for (int i = 0; i < items.length; i++) {
            APN apn = apns.get(i);
            items[i] = (i+1) + ". " + apn.name;

            if (apn.mmsc.equals(curMmsc) && apn.proxy.equals(curProxy) && apn.port.equals(curPort)) {
                defaultApn = i;
            }
        }

        new AlertDialog.Builder(context)
                .setSingleChoiceItems(items, defaultApn, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        setApns(context, apns.get(i));
                        if (listener != null) {
                            listener.onFinished();
                        }
                        dialogInterface.dismiss();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if (listener != null) {
                            listener.onFinished();
                        }
                    }
                })
                .show();
    }

    public interface OnApnFinishedListener {
        public abstract void onFinished();
    }

    private static void loadMmsSettings(Context context) {
        XmlResourceParser parser = context.getResources().getXml(R.xml.mms_config);
        String maxMessageSize = 1000000 + "";
        int maxImageHeight = 800;
        int maxImageWidth = 800;
        String userAgent = "Android Messaging";
        String uaProfUrl = "http://www.gstatic.com/android/hangouts/hangouts_mms_ua_profile.xml";

        try {
            beginDocument(parser, "mms_config");

            while (true) {
                nextElement(parser);
                String tag = parser.getName();
                if (tag == null) {
                    break;
                }
                String name = parser.getAttributeName(0);
                String value = parser.getAttributeValue(0);
                String text = null;
                if (parser.next() == XmlPullParser.TEXT) {
                    text = parser.getText();
                }

                Log.v(TAG, "mms_config entry: " + tag + "/" + value);
                if ("name".equalsIgnoreCase(name)) {
                    if ("int".equals(tag)) {
                        // int config tags go here
                        if ("maxMessageSize".equalsIgnoreCase(value)) {
                            maxMessageSize = text;
                        } else if ("maxImageHeight".equalsIgnoreCase(value)) {
                            maxImageHeight = Integer.parseInt(text);
                        } else if ("maxImageWidth".equalsIgnoreCase(value)) {
                            maxImageWidth = Integer.parseInt(text);
                        }
                    } else if ("string".equals(tag)) {
                        // string config tags go here
                        if ("userAgent".equalsIgnoreCase(value)) {
                            userAgent = text;
                        } else if ("uaProfUrl".equalsIgnoreCase(value)) {
                            uaProfUrl = text;
                        }
                    }
                }
            }
        } catch (XmlPullParserException e) {
            Log.e(TAG, "loadMmsSettings caught ", e);
        } catch (NumberFormatException e) {
            Log.e(TAG, "loadMmsSettings caught ", e);
        } catch (IOException e) {
            Log.e(TAG, "loadMmsSettings caught ", e);
        } finally {
            parser.close();
        }

        String errorStr = null;

        if (errorStr != null) {
            String err =
                    String.format("MmsConfig.loadMmsSettings mms_config.xml missing %s setting",
                            errorStr);
            Log.e(TAG, err);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final TelephonyManager telephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            userAgent = telephonyManager.getMmsUserAgent();
            uaProfUrl = telephonyManager.getMmsUAProfUrl();
        }

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt("mms_max_width", maxImageWidth)
                .putInt("mms_max_height", maxImageHeight)
                .putString("mms_max_size", maxMessageSize)
                .putString("mms_agent", userAgent)
                .putString("mms_user_agent_profile_url", uaProfUrl)
                .commit();
    }

    private static ArrayList<APN> loadApns(Context context) {
        XmlResourceParser parser = context.getResources().getXml(R.xml.apns);
        ArrayList<APN> apns = new ArrayList<APN>();
        String mmsc = "", proxy = "", port = "", carrier = "";

        int mcc = -1, mnc = -1;

        TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String networkOperator = manager.getNetworkOperator();

        if (isValidNetworkOperator(networkOperator)) {
            mcc = Integer.parseInt(networkOperator.substring(0, 3));
            String s = networkOperator.substring(3);
            try {
                mnc = Integer.parseInt(s.replaceFirst("^0{1,2}", ""));
            } catch (Exception e) {
                mnc = -1;
            }
        } else {
            mcc = context.getResources().getConfiguration().mcc;
            mnc = context.getResources().getConfiguration().mnc;
        }

        try {
            if (mcc == -1) {
                mcc = Integer.parseInt(new ServiceState().getOperatorNumeric().substring(0, 3));
            }

            if (mnc == -1) {
                TelephonyManager tm  = (TelephonyManager) context.getSystemService
                        (Context.TELEPHONY_SERVICE);
                mnc = ((CdmaCellLocation) tm.getCellLocation()).getSystemId();
            }
        } catch (Exception e) {

        }

        if (mcc == -1 || mnc == -1) {
            Log.v(TAG, "couldn't find both mcc and mnc. mcc = " + mcc + ", mnc = " + mnc);
            return null;
        }

        Log.v(TAG, "mcc: " + mcc + " mnc: " + mnc);

        try {
            beginDocument(parser, "apns");

            while (true) {
                nextElement(parser);
                String tag = parser.getName();
                if (tag == null) {
                    break;
                }

                boolean mccCorrect = false, mncCorrect = false;
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    try {
                        String name = parser.getAttributeName(i);
                        int value = Integer.parseInt(parser.getAttributeValue(i));
                        if ("mcc".equals(name) && mcc == value) {
                            mccCorrect = true;
                        } else if ("mnc".equals(name) && mnc == value) {
                            mncCorrect = true;
                        }
                    } catch (Exception e) {
                        // cast exception probably
                    }
                }

                if (mccCorrect && mncCorrect) {
                    // parse the rest of the apn
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String name = parser.getAttributeName(i);
                        String value = parser.getAttributeValue(i);
                        if ("type".equals(name)) {
                            if (!value.contains("mms")) {
                                mmsc = "";
                                proxy = "";
                                port = "";
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
                        } else if ("port".equals(name) && port.equals("")) {
                            port = value;
                        }
                    }

                    if (!mmsc.equals("")) {
                        APN apn = new APN();
                        apn.name = carrier;
                        apn.mmsc = mmsc;
                        apn.proxy = proxy;
                        apn.port = port;

                        boolean contains = false;
                        for (int i = 0; i < apns.size(); i++) {
                            APN current = apns.get(i);

                            if (current.mmsc.equals(apn.mmsc) && current.port.equals(apn.port) && current.proxy.equals(apn.proxy)) {
                                contains = true;
                                break;
                            }
                        }

                        if (!contains) {
                            apns.add(apn);
                        }
                    }
                }
            }
        } catch (XmlPullParserException e) {
            Log.e(TAG, "loadApns caught ", e);
        } catch (NumberFormatException e) {
            Log.e(TAG, "loadApns caught ", e);
        } catch (IOException e) {
            Log.e(TAG, "loadApns caught ", e);
        } finally {
            parser.close();
        }

        Log.v(TAG, "APN resolved (mmsc=" + LogRedaction.redactUrl(mmsc)
                + ", proxy=" + (TextUtils.isEmpty(proxy) ? "none" : "set") + ")");

        String errorStr = null;

        if (errorStr != null) {
            String err =
                    String.format("MmsConfig.loadMmsSettings mms_config.xml missing %s setting",
                            errorStr);
            Log.e(TAG, err);
        }

        return apns;
    }

    /**
     * Checks if the given network operator is valid, i.e. it's not null, empty or contains "null"
     *
     * @param networkOperator
     *         The network operator to be checked
     *
     * @return True if is a valid operator
     */
    private static boolean isValidNetworkOperator(String networkOperator) {
      return networkOperator != null && !networkOperator.isEmpty() &&
              !networkOperator.contains("null");
    }

    private static void setApns(Context context, APN apn) {
        MmsApnResolver.saveSelectedApn(
                context,
                Utils.getDefaultSubscriptionId(),
                new MmsApn(apn.name, apn.mmsc, apn.proxy, apn.port,
                        Utils.getDefaultSubscriptionId()));
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString("mmsc_url", apn.mmsc)
                .putString("mms_proxy", apn.proxy)
                .putString("mms_port", apn.port)
                .commit();
    }

    private static ArrayList<APN> toLegacyApns(List<MmsApn> apns) {
        final ArrayList<APN> legacy = new ArrayList<>();
        if (apns == null) {
            return legacy;
        }
        for (MmsApn apn : apns) {
            APN item = new APN();
            item.name = apn.getName();
            item.mmsc = apn.getMmsc();
            item.proxy = apn.getProxy();
            item.port = apn.getPort();
            legacy.add(item);
        }
        return legacy;
    }

    private static void beginDocument(XmlPullParser parser, String firstElementName) throws XmlPullParserException, IOException {
        int type;
        while ((type=parser.next()) != parser.START_TAG
                && type != parser.END_DOCUMENT) {
            ;
        }

        if (type != parser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                    ", expected " + firstElementName);
        }
    }

    private static void nextElement(XmlPullParser parser) throws XmlPullParserException, IOException {
        int type;
        while ((type=parser.next()) != parser.START_TAG
                && type != parser.END_DOCUMENT) {
            ;
        }
    }

    private static class APN {
        public String name;
        public String mmsc;
        public String proxy;
        public String port;
    }
}
