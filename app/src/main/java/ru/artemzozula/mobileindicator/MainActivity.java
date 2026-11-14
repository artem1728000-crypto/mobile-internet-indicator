package ru.artemzozula.mobileindicator;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;
    private Button start;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText("Мобильный интернет");
        title.setTextSize(28);
        layout.addView(title);
        status = new TextView(this);
        status.setTextSize(18);
        layout.addView(status);
        TextView legend = new TextView(this);
        legend.setText("✓  Внешний доступ есть\n!   Яндекс доступен, вероятно белый список\n×  Мобильный доступ отсутствует\n•••  Идёт проверка");
        legend.setTextSize(17);
        legend.setPadding(0, padding, 0, padding);
        layout.addView(legend);
        start = new Button(this);
        start.setOnClickListener(view -> enable());
        layout.addView(start);
        Button stop = new Button(this);
        stop.setText("Выключить наблюдение");
        stop.setOnClickListener(view -> {
            BootReceiver.setEnabled(this, false);
            stopService(new Intent(this, MonitorService.class));
            refresh();
        });
        layout.addView(stop);
        setContentView(layout);

        if (!getPreferences(MODE_PRIVATE).getBoolean("opened", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("opened", true).apply();
            enable();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private boolean notificationsAvailable() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        android.app.NotificationChannel channel = manager.getNotificationChannel(MonitorService.CHANNEL);
        return manager.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    private void enable() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("asked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else if (!notificationsAvailable()) {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } else {
            BootReceiver.setEnabled(this, true);
            startForegroundService(new Intent(this, MonitorService.class));
            refresh();
        }
    }

    private void refresh() {
        boolean available = notificationsAvailable();
        status.setText(!available ? "Разреши уведомления для значка в строке состояния"
                : BootReceiver.enabled(this) ? "Наблюдение включено" : "Наблюдение выключено");
        start.setText(available ? "Включить наблюдение" : "Разрешить уведомления");
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1 && notificationsAvailable()) enable();
        refresh();
    }
}
