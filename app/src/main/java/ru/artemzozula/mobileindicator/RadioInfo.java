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

    static String cellDetails(Context context) {
        String unavailable = "Android не сообщил данные обслуживающей соты LTE.";
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return unavailable;
        try {
            TelephonyManager base = context.getSystemService(TelephonyManager.class);
            if (base == null) return unavailable;
            int subscription = Build.VERSION.SDK_INT >= 30 ? SubscriptionManager.getActiveDataSubscriptionId()
                    : SubscriptionManager.getDefaultDataSubscriptionId();
            if (subscription < 0) return unavailable;
            java.util.List<CellInfo> cells = base.createForSubscriptionId(subscription).getAllCellInfo();
            if (cells == null) return unavailable;
            CellInfoLte primary = null;
            for (CellInfo cell : cells) {
                if (!(cell instanceof CellInfoLte) || !cell.isRegistered()) continue;
                if (primary != null) return "Android сообщил несколько обслуживающих сот LTE.";
                primary = (CellInfoLte) cell;
            }
            if (primary == null) return unavailable;
            CellIdentityLte id = primary.getCellIdentity();
            CellSignalStrengthLte strength = primary.getCellSignalStrength();
            StringBuilder text = new StringBuilder();
            if (Build.VERSION.SDK_INT >= 30 && id.getBands().length > 0) {
                text.append("Диапазоны ").append(java.util.Arrays.toString(id.getBands())).append('\n');
            }
            append(text, "EARFCN", id.getEarfcn(), "");
            append(text, "PCI", id.getPci(), "");
            if (Build.VERSION.SDK_INT >= 28) append(text, "Ширина", id.getBandwidth(), " кГц");
            append(text, "RSRP", strength.getRsrp(), " дБм");
            append(text, "RSRQ", strength.getRsrq(), " дБ");
            append(text, "RSSNR", strength.getRssnr(), " дБ");
            return text.length() == 0 ? unavailable : text.toString().trim();
        } catch (SecurityException | UnsupportedOperationException ignored) {
            return unavailable;
        }
    }

    private static void append(StringBuilder text, String label, int value, String unit) {
        if (value != Integer.MAX_VALUE && value != -1) {
            text.append(label).append(' ').append(value).append(unit).append('\n');
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
