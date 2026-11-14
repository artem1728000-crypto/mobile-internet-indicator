package ru.artemzozula.mobileindicator;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

record WifiLink(int rssi, int frequency, int channel, int speed) {
    @SuppressWarnings("deprecation")
    static WifiLink read(Context context, Network network) {
        if (network == null) return null;
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null;

        WifiInfo info;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                info = caps.getTransportInfo() instanceof WifiInfo
                        ? (WifiInfo) caps.getTransportInfo() : null;
            } else {
                WifiManager wifi = context.getApplicationContext().getSystemService(WifiManager.class);
                info = wifi == null ? null : wifi.getConnectionInfo();
            }
        } catch (SecurityException | UnsupportedOperationException exception) {
            info = null;
        }
        if (info == null) return new WifiLink(0, 0, -1, 0);
        int frequency = info.getFrequency();
        return new WifiLink(info.getRssi(), frequency, channelFor(frequency), info.getLinkSpeed());
    }

    static int channelFor(int mhz) {
        if (Build.VERSION.SDK_INT >= 31) {
            return ScanResult.convertFrequencyMhzToChannelIfSupported(mhz);
        }
        if (mhz == 2484) return 14;
        if (mhz >= 2412 && mhz <= 2472 && (mhz - 2412) % 5 == 0) return (mhz - 2407) / 5;
        if (mhz >= 5160 && mhz <= 5885 && mhz % 5 == 0) return (mhz - 5000) / 5;
        if (mhz == 5935) return 2;
        if (mhz >= 5955 && mhz <= 7115 && mhz % 5 == 0) return (mhz - 5950) / 5;
        if (mhz >= 58320 && mhz <= 70200 && (mhz - 58320) % 2160 == 0) return (mhz - 56160) / 2160;
        return -1;
    }
}
