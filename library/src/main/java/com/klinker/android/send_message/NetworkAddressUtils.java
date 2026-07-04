package com.klinker.android.send_message;

/**
 * Small network string helpers (formerly vendored in {@code android.net.NetworkUtilsHelper}).
 */
public final class NetworkAddressUtils {

    private NetworkAddressUtils() {
    }

    public static String trimV4AddrZeros(String addr) {
        if (addr == null) {
            return null;
        }
        String[] octets = addr.split("\\.");
        if (octets.length != 4) {
            return addr;
        }
        StringBuilder builder = new StringBuilder(16);
        for (int i = 0; i < 4; i++) {
            try {
                if (octets[i].length() > 3) {
                    return addr;
                }
                builder.append(Integer.parseInt(octets[i]));
            } catch (NumberFormatException e) {
                return addr;
            }
            if (i < 3) {
                builder.append('.');
            }
        }
        return builder.toString();
    }
}