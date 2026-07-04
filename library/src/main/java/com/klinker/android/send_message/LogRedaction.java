package com.klinker.android.send_message;

import android.net.Uri;
import android.text.TextUtils;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Helpers for log lines that must not contain message content, contact identifiers, or full URLs.
 */
public final class LogRedaction {

    private LogRedaction() {
    }

    public static String redactUrl(String urlString) {
        if (TextUtils.isEmpty(urlString)) {
            return urlString;
        }
        String protocol = "http";
        String host = "";
        try {
            final URL url = new URL(urlString);
            protocol = url.getProtocol();
            host = url.getHost();
        } catch (MalformedURLException ignored) {
        }
        return protocol + "://" + (TextUtils.isEmpty(host) ? "*" : host)
                + "[" + urlString.length() + "]";
    }

    public static String redactUri(Uri uri) {
        if (uri == null) {
            return "null";
        }
        final String scheme = uri.getScheme();
        return (scheme != null ? scheme : "content") + ":***";
    }

    public static String redactPath(String path) {
        if (path == null) {
            return "null";
        }
        return "[path:" + path.length() + "b]";
    }
}