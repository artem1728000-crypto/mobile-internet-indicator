package ru.artemzozula.mobileindicator;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.*;

final class RadioInfo {
    private TelephonyManager manager;
    private PhoneStateListener listener;
    private TelephonyDisplayInfo display;
    private int subscription = -1;

    @SuppressWarnings("deprecation")
    String read(Context context) {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) return "";
        try {
            int id = Build.VERSION.SDK_INT >= 30 ? SubscriptionManager.getActiveDataSubscriptionId()
                    : SubscriptionManager.getDefaultDataSubscriptionId();
            if (id < 0) return "";
            if (manager == null || id != subscription) {
                close();
                TelephonyManager base = context.getSystemService(TelephonyManager.class);
                if (base == null) return "";
                manager = base.createForSubscriptionId(id);
                subscription = id;
                if (Build.VERSION.SDK_INT >= 30) {
                    listener = new PhoneStateListener() {
                        @Override public void onDisplayInfoChanged(TelephonyDisplayInfo info) {
                            if (listener == this) display = info;
                        }
                    };
                    manager.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED);
                }
            }
            int type = manager.getDataNetworkType();
            String name = switch (type) {
                case TelephonyManager.NETWORK_TYPE_LTE -> "LTE";
                case TelephonyManager.NETWORK_TYPE_NR -> "5G";
                case TelephonyManager.NETWORK_TYPE_HSPAP -> "H+";
                case TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
                     TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA";
                case TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
                     TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B,
                     TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G";
                case TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE";
                case TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_GSM,
                     TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT -> "2G";
                default -> "";
            };
            if (Build.VERSION.SDK_INT >= 30 && display != null && display.getNetworkType() == type) {
                int override = display.getOverrideNetworkType();
                if (override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA) name = "LTE+ (CA)";
                if (override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA) name = "LTE / значок 5G NSA";
            }
            return name;
        } catch (SecurityException | UnsupportedOperationException ignored) {
            return "";
        }
    }

    int strength() {
        if (manager == null || Build.VERSION.SDK_INT < 29) return Integer.MAX_VALUE;
        try {
            SignalStrength signal = manager.getSignalStrength();
            if (signal == null || signal.getCellSignalStrengths().isEmpty()) return Integer.MAX_VALUE;
            int type = manager.getDataNetworkType();
            for (CellSignalStrength cell : signal.getCellSignalStrengths()) {
                if (type == TelephonyManager.NETWORK_TYPE_LTE && cell instanceof CellSignalStrengthLte
                        || type == TelephonyManager.NETWORK_TYPE_NR && cell instanceof CellSignalStrengthNr
                        || type != TelephonyManager.NETWORK_TYPE_LTE && type != TelephonyManager.NETWORK_TYPE_NR) {
                    int dbm = cell.getDbm();
                    if (dbm >= -160 && dbm < 0) return dbm;
                }
            }
        } catch (SecurityException | UnsupportedOperationException ignored) {
            return Integer.MAX_VALUE;
        }
        return Integer.MAX_VALUE;
    }

    @SuppressWarnings("deprecation")
    void close() {
        PhoneStateListener previous = listener;
        listener = null;
        if (previous != null && manager != null) {
            try {
                manager.listen(previous, PhoneStateListener.LISTEN_NONE);
            } catch (SecurityException | UnsupportedOperationException ignored) {
                // Permission or radio availability can change while monitoring.
            }
        }
        manager = null;
        display = null;
    }
}
