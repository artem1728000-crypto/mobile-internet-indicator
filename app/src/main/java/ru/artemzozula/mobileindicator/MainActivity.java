package ru.artemzozula.mobileindicator;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;

import java.text.DateFormat;
import java.util.Date;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;
    private Button start;
    private Button check;
    private ImageView icon;
    private TextView measurements;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable render = new Runnable() {
        @Override public void run() {
            renderSnapshot();
            main.postDelayed(this, 500);
        }
    };

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
        icon = new ImageView(this);
        icon.setColorFilter(0xFF123367);
        int iconSize = Math.round(42 * getResources().getDisplayMetrics().density);
        layout.addView(icon, iconSize, iconSize);
        measurements = new TextView(this);
        measurements.setTextSize(17);
        measurements.setPadding(0, 0, 0, padding);
        layout.addView(measurements);

        RadioGroup mode = new RadioGroup(this);
        RadioButton active = new RadioButton(this);
        active.setId(android.view.View.generateViewId());
        active.setText("Активное подключение (Wi-Fi или SIM)");
        mode.addView(active);
        RadioButton mobile = new RadioButton(this);
        mobile.setId(android.view.View.generateViewId());
        mobile.setText("Только мобильная сеть (SIM этого телефона)");
        mode.addView(mobile);
        mode.check(BootReceiver.mobileOnly(this) ? mobile.getId() : active.getId());
        mode.setOnCheckedChangeListener((group, checked) -> {
            BootReceiver.setMobileOnly(this, checked == mobile.getId());
            command(MonitorService.ACTION_MODE);
        });
        layout.addView(mode);

        check = new Button(this);
        check.setText("Проверить сейчас");
        check.setOnClickListener(view -> command(MonitorService.ACTION_REFRESH));
        layout.addView(check);
        Button history = new Button(this);
        history.setText("История по дням");
        history.setOnClickListener(view -> startActivity(new Intent(this, HistoryActivity.class)));
        layout.addView(history);
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
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return insets;
        });
        setContentView(scroll);

        if (!getPreferences(MODE_PRIVATE).getBoolean("opened", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("opened", true).apply();
            enable();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        main.post(render);
    }

    @Override protected void onPause() {
        main.removeCallbacks(render);
        super.onPause();
    }

    private void command(String action) {
        if (BootReceiver.enabled(this)) {
            startForegroundService(new Intent(this, MonitorService.class).setAction(action));
        }
    }

    private void renderSnapshot() {
        boolean enabled = BootReceiver.enabled(this);
        MonitorService.Snapshot current = enabled ? MonitorService.snapshot : null;
        check.setEnabled(enabled && current != null && !"Нет подключения".equals(current.transport()));
        if (current == null) {
            icon.setImageResource(R.drawable.ic_checking);
            measurements.setText(enabled ? "Запуск наблюдения…" : "Наблюдение выключено");
            return;
        }
        icon.setImageResource(current.icon());
        String summary = current.transport() + "\n" + current.status();
        if (current.checkedAt() > 0) {
            summary += "\n\n8.8.8.8:53 · TCP  " + reading(current.externalMs())
                    + "\nya.ru · HTTPS  " + reading(current.yandexMs())
                    + "\n\nПоследняя проверка  "
                    + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(current.checkedAt()));
            if (current.confirming()) summary += "\nОжидается повторная проверка сбоя";
        }
        measurements.setText(summary);
    }

    private static String reading(long millis) {
        return millis < 0 ? "нет ответа" : millis + " мс";
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
