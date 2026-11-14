package ru.artemzozula.mobileindicator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    static boolean enabled(Context context) {
        return context.getSharedPreferences("monitor", Context.MODE_PRIVATE).getBoolean("enabled", false);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("monitor", Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", enabled).apply();
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && enabled(context)) {
            context.startForegroundService(new Intent(context, MonitorService.class));
        }
    }
}
