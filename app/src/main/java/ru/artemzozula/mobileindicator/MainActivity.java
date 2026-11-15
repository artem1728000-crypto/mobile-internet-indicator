package ru.artemzozula.mobileindicator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

public final class MainActivity extends Activity {
    private TextView transport, headline, detail, external, yandex;
    private LinearLayout hero, wifiCard;
    private TextView wifiSignal, wifiFrequency, wifiChannel, wifiSpeed, wifiAddress;
    private Button wifiGateway;
    private final Runnable renderWifi = new Runnable() {
        @Override public void run() {
            updateWifi();
            main.postDelayed(this, 2000);
        }
    };
    private SignalView signal;
    private Button monitor, check, activeMode, mobileMode, updates;
    private MonitorService.Snapshot rendered;
    private boolean renderedEnabled;
    private boolean needsRender = true;
    private boolean checkingRelease;
    private ReleaseCheck.Release availableRelease;
    private String version;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable render = new Runnable() {
        @Override public void run() {
            renderSnapshot();
            main.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new java.io.File(getCacheDir(), "update.apk").delete();
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException(error);
        }
        LinearLayout page = Ui.screen(this);
        Ui.add(page, Ui.text(this, "Мастер роутеров", 29, Ui.INK, true), 0);
        hero = Ui.column(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(hero, 18, 20);
        Ui.add(page, hero, 19);
        transport = Ui.text(this, "", 13, Ui.BLUE, true);
        transport.setGravity(Gravity.CENTER);
        Ui.add(hero, transport, 0);
        signal = new SignalView(this);
        hero.addView(signal, Ui.dp(this, 128), Ui.dp(this, 118));
        headline = Ui.text(this, "", 24, Ui.INK, true);
        headline.setGravity(Gravity.CENTER);
        Ui.add(hero, headline, 0);
        detail = Ui.text(this, "", 13, Ui.MUTED, false);
        detail.setGravity(Gravity.CENTER);
        Ui.add(hero, detail, 8);

        LinearLayout readings = new LinearLayout(this);
        external = readingCard(readings, "8.8.8.8:53", "Открытие TCP");
        yandex = readingCard(readings, "ya.ru", "Ответ HTTPS");
        Ui.add(page, readings, 12);
        wifiCard = Ui.column(this);
        Ui.pad(wifiCard, 16, 14);
        wifiCard.setBackground(Ui.shape(this, Ui.SURFACE, Ui.LINE, 18));
        Ui.add(wifiCard, Ui.text(this, "Связь по Wi-Fi", 17, Ui.INK, true), 0);
        wifiSignal = wifiRow("Сигнал");
        wifiFrequency = wifiRow("Частота");
        wifiChannel = wifiRow("Канал");
        wifiSpeed = wifiRow("Скорость до точки доступа");
        wifiAddress = wifiRow("IP телефона");
        wifiGateway = Ui.button(this, "Шлюз", false, this::openRouter);
        Ui.add(wifiCard, wifiGateway, 10);
        wifiCard.setVisibility(android.view.View.GONE);
        Ui.add(page, wifiCard, 12);
        check = Ui.button(this, "Проверить сейчас", true, () -> command(MonitorService.ACTION_REFRESH));
        Ui.add(page, check, 14);
        Ui.add(page, Ui.button(this, "Измерить задержку DNS", false, this::measureLatency), 8);
        Ui.add(page, Ui.button(this, "История по дням  ›", false,
                () -> startActivity(new Intent(this, HistoryActivity.class))), 8);

        Ui.add(page, Ui.text(this, "Проверяемая сеть", 17, Ui.INK, true), 24);
        LinearLayout modes = new LinearLayout(this);
        activeMode = Ui.button(this, "Активная сеть", false, () -> setMode(false));
        mobileMode = Ui.button(this, "Только SIM", false, () -> setMode(true));
        addHalf(modes, activeMode);
        addHalf(modes, mobileMode);
        Ui.add(page, modes, 8);
        Ui.add(page, Ui.text(this, "Для Wi-Fi и раздачи выбери активную сеть.", 13, Ui.MUTED, false), 8);
        monitor = Ui.button(this, "", false, this::toggle);
        Ui.add(page, monitor, 16);
        Ui.add(page, Ui.button(this, "Радиосеть", false, this::showRadio), 8);
        updates = Ui.button(this, "Проверить обновления", false, () -> {
            if (availableRelease != null) showRelease(availableRelease);
            else checkRelease(true);
        });
        Ui.add(page, updates, 8);
        Ui.add(page, Ui.text(this, "Версия " + version, 12, Ui.MUTED, false), 12);
        showMode();

        if (!getPreferences(MODE_PRIVATE).getBoolean("opened", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("opened", true).apply();
            enable();
        }
    }

    private void showRadio() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Радиосеть")
                    .setMessage("Разрешение «Телефон» нужно для чтения стандарта сети и сигнала SIM.")
                    .setNegativeButton("Позже", null)
                    .setPositiveButton("Разрешить", (dialog, button) ->
                            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 2)).show();
            return;
        }
        MonitorService.Snapshot current = MonitorService.snapshot;
        String message = current == null || current.standard().isEmpty()
                ? "Нет данных о радиосети. Для проверки своей SIM выбери «Только SIM» и дождись замера."
                : current.standard() + (current.dbm() == Integer.MAX_VALUE ? "" : "\nСигнал " + current.dbm() + " дБм")
                    + (current.standard().equals("LTE") ? "\nНет данных об агрегации" : "");
        new AlertDialog.Builder(this).setTitle("Радиосеть").setMessage(message)
                .setNeutralButton("Данные соты", (dialog, button) -> showCell())
                .setPositiveButton("ОК", null).show();
    }

    private void showCell() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Данные соты")
                    .setMessage("Android требует точную геолокацию для чтения диапазона и канала. Данные соты показываются здесь по запросу.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Разрешить", (dialog, button) -> requestPermissions(new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 3))
                    .show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Обслуживающая сота LTE")
                .setMessage(RadioInfo.cellDetails(this)).setPositiveButton("ОК", null).show();
    }

    private void measureLatency() {
        android.net.Network network = MonitorService.probeNetwork;
        if (network == null) {
            new AlertDialog.Builder(this).setMessage("Сначала включи наблюдение и дождись подключения.")
                    .setPositiveButton("ОК", null).show();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Задержка DNS")
                .setMessage("Отправляем три запроса по UDP к 8.8.8.8…")
                .setPositiveButton("Закрыть", null).show();
        new Thread(() -> {
            String result = DnsLatency.measure(network);
            runOnUiThread(() -> {
                if (!dialog.isShowing() || isDestroyed()) return;
                dialog.setMessage(network.equals(MonitorService.probeNetwork)
                        ? result + "\n\nМедиана ответов DNS по UDP. Тайм-аут запроса — 1 с."
                        : "Подключение сменилось. Повтори замер.");
            });
        }, "dns-latency").start();
    }

    private TextView wifiRow(String label) {
        LinearLayout row = Ui.column(this);
        Ui.add(row, Ui.text(this, label, 12, Ui.MUTED, false), 0);
        TextView value = Ui.text(this, "—", 16, Ui.BLUE, true);
        Ui.add(row, value, 1);
        Ui.add(wifiCard, row, 8);
        return value;
    }

    private void updateWifi() {
        WifiLink link = BootReceiver.enabled(this) && !BootReceiver.mobileOnly(this)
                ? WifiLink.read(this, MonitorService.routerNetwork) : null;
        wifiCard.setVisibility(link == null ? android.view.View.GONE : android.view.View.VISIBLE);
        if (link == null) return;
        wifiSignal.setText(link.rssi() > -127 && link.rssi() < 0 ? link.rssi() + " дБм" : "—");
        wifiFrequency.setText(link.frequency() > 0 ? link.frequency() + " МГц" : "—");
        wifiChannel.setText(link.channel() > 0 ? Integer.toString(link.channel()) : "—");
        wifiSpeed.setText(link.speed() > 0 ? link.speed() + " Мбит/с" : "—");
        RouterLink router = MonitorService.routerLink;
        wifiAddress.setText(router == null || router.address().isEmpty() ? "Получение IPv4…" : router.address());
        boolean gatewayReady = router != null && !router.gateway().isEmpty();
        wifiGateway.setEnabled(gatewayReady);
        wifiGateway.setText(gatewayReady ? "Шлюз " + router.gateway() + "  ›" : "Шлюз —");
    }

    private void openRouter() {
        RouterLink router = MonitorService.routerLink;
        if (router == null || router.gateway().isEmpty() || MonitorService.routerNetwork == null || BootReceiver.mobileOnly(this)) return;
        try {
            startActivity(router.browserIntent(this, MonitorService.routerNetwork));
        } catch (android.content.ActivityNotFoundException error) {
            android.widget.Toast.makeText(this, "Не удалось открыть настройки роутера",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private TextView readingCard(LinearLayout row, String address, String protocol) {
        LinearLayout card = Ui.column(this);
        Ui.pad(card, 14, 13);
        card.setBackground(Ui.shape(this, Ui.SURFACE, Ui.LINE, 18));
        Ui.add(card, Ui.text(this, address, 14, Ui.INK, true), 0);
        TextView value = Ui.text(this, "—", 19, Ui.BLUE, true);
        Ui.add(card, value, 5);
        Ui.add(card, Ui.text(this, protocol, 12, Ui.MUTED, false), 4);
        addHalf(row, card);
        return value;
    }

    private void addHalf(LinearLayout row, android.view.View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        if (row.getChildCount() > 0) params.setMarginStart(Ui.dp(this, 8));
        row.addView(view, params);
    }

    @Override protected void onResume() {
        super.onResume();
        if (BootReceiver.enabled(this) && MonitorService.snapshot == null) {
            startForegroundService(new Intent(this, MonitorService.class));
        }
        needsRender = true;
        main.post(render);
        main.post(renderWifi);
        if (availableRelease == null && System.currentTimeMillis() - getPreferences(MODE_PRIVATE).getLong("releaseChecked", 0)
                >= 86_400_000L) checkRelease(false);
    }

    @Override protected void onPause() {
        main.removeCallbacks(render);
        main.removeCallbacks(renderWifi);
        super.onPause();
    }

    private void setMode(boolean mobile) {
        BootReceiver.setMobileOnly(this, mobile);
        showMode();
        command(MonitorService.ACTION_MODE);
        updateWifi();
    }

    private void showMode() {
        boolean mobile = BootReceiver.mobileOnly(this);
        activeMode.setTextColor(mobile ? Ui.MUTED : Ui.BLUE);
        mobileMode.setTextColor(mobile ? Ui.BLUE : Ui.MUTED);
        activeMode.setSelected(!mobile);
        mobileMode.setSelected(mobile);
    }

    private void command(String action) {
        if (BootReceiver.enabled(this)) {
            startForegroundService(new Intent(this, MonitorService.class).setAction(action));
        }
    }

    private void renderSnapshot() {
        boolean enabled = BootReceiver.enabled(this);
        MonitorService.Snapshot current = enabled ? MonitorService.snapshot : null;
        if (!needsRender && current == rendered && enabled == renderedEnabled) return;
        needsRender = false;
        rendered = current;
        updateWifi();
        renderedEnabled = enabled;
        int icon = current == null ? R.drawable.ic_checking : current.icon();
        int accent = icon == R.drawable.ic_full ? Ui.GREEN : icon == R.drawable.ic_limited ? Ui.AMBER
                : icon == R.drawable.ic_none ? Ui.RED : Ui.BLUE;
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF192638, Ui.BACK, Ui.SURFACE});
        background.setCornerRadius(Ui.dp(this, 26));
        background.setStroke(Ui.dp(this, 1), Ui.LINE);
        hero.setBackground(background);
        transport.setText(current == null ? "Мониторинг" : current.standard().isEmpty()
                ? current.transport() : current.standard() + (current.dbm() == Integer.MAX_VALUE
                ? "" : " · " + current.dbm() + " дБм"));
        transport.setTextColor(accent);
        signal.show(icon, accent);
        headline.setText(current == null ? (enabled ? "Запуск наблюдения…" : "Наблюдение на паузе") : current.status());
        boolean measured = current != null && current.checkedAt() > 0;
        detail.setText(measured ? "Проверено в "
                + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(current.checkedAt()))
                + (current.confirming() ? "\nПроверяем, повторится ли сбой" : "")
                : enabled ? "Автоматические проверки в фоне" : "Включи наблюдение, чтобы начать замеры");
        external.setText(measured ? reading(current.externalMs()) : "—");
        yandex.setText(measured ? reading(current.yandexMs()) : "—");
        check.setEnabled(current != null && !"Нет подключения".equals(current.transport()));
        check.setAlpha(check.isEnabled() ? 1f : .55f);
        monitor.setText(!notificationsAvailable() ? "Разрешить уведомления"
                : enabled ? "Приостановить наблюдение" : "Включить наблюдение");
    }

    private static String reading(long millis) {
        return millis < 0 ? "Нет ответа" : millis + " мс";
    }

    private boolean notificationsAvailable() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        android.app.NotificationChannel channel = manager.getNotificationChannel(MonitorService.CHANNEL);
        return manager.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    private void toggle() {
        if (!notificationsAvailable() || !BootReceiver.enabled(this)) {
            enable();
        } else {
            BootReceiver.setEnabled(this, false);
            stopService(new Intent(this, MonitorService.class));
            updateWifi();
            renderSnapshot();
        }
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
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1 && notificationsAvailable()) enable();
        if (request == 2) command(MonitorService.ACTION_REFRESH);
        if (request == 3 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) showCell();
        needsRender = true;
    }

    private void checkRelease(boolean manual) {
        if (checkingRelease) return;
        checkingRelease = true;
        if (manual) updates.setText("Проверяем GitHub…");
        new Thread(() -> {
            try {
                ReleaseCheck.Release release = ReleaseCheck.latest(version);
                runOnUiThread(() -> finishCheck(manual, release, false));
            } catch (Exception error) {
                runOnUiThread(() -> finishCheck(manual, null, true));
            }
        }, "github-release").start();
    }

    private void finishCheck(boolean manual, ReleaseCheck.Release release, boolean failed) {
        checkingRelease = false;
        if (isFinishing() || isDestroyed()) return;
        if (!failed && release == null) {
            getPreferences(MODE_PRIVATE).edit().putLong("releaseChecked", System.currentTimeMillis()).apply();
        }
        availableRelease = release;
        updates.setText(release == null ? "Проверить обновления" : "Доступно обновление " + release.version() + "  ↓");
        if (!manual) return;
        if (release != null) showRelease(release);
        else new AlertDialog.Builder(this)
                .setMessage(failed ? "GitHub не ответил. Повтори проверку позже." : "Установлена актуальная версия.")
                .setPositiveButton("OK", null).show();
    }

    private void showRelease(ReleaseCheck.Release release) {
        new AlertDialog.Builder(this).setTitle("Версия " + release.version())
                .setMessage("APK будет скачан в кэш приложения. Установку подтвердит Android.")
                .setNegativeButton("Позже", null)
                .setPositiveButton("Обновить", (dialog, which) -> installRelease(release)).show();
    }

    private void installRelease(ReleaseCheck.Release release) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                android.widget.Toast.makeText(this, "Разреши установку и снова нажми «Обновить»",
                        android.widget.Toast.LENGTH_LONG).show();
            } catch (android.content.ActivityNotFoundException error) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(release.download())));
            }
            return;
        }
        updates.setEnabled(false);
        updates.setText("Скачиваем обновление…");
        new Thread(() -> {
            java.io.File apk = new java.io.File(getCacheDir(), "update.apk");
            javax.net.ssl.HttpsURLConnection connection = null;
            try {
                connection = (javax.net.ssl.HttpsURLConnection) new java.net.URL(release.download()).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                try (java.io.InputStream input = connection.getInputStream();
                     java.io.OutputStream output = new java.io.FileOutputStream(apk)) {
                    byte[] buffer = new byte[8192];
                    long size = 0;
                    for (int count; (count = input.read(buffer)) != -1;) {
                        size += count;
                        if (size > 32L * 1024 * 1024) throw new java.io.IOException("APK too large");
                        output.write(buffer, 0, count);
                    }
                }
                android.content.pm.PackageInfo info = getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
                if (info == null || !getPackageName().equals(info.packageName)
                        || !release.version().equals(info.versionName)) throw new java.io.IOException("Unexpected APK");
                runOnUiThread(() -> {
                    updates.setEnabled(true);
                    updates.setText("Доступно обновление " + release.version());
                    if (isFinishing() || isDestroyed()) return;
                    Uri uri = Uri.parse("content://" + getPackageName() + ".updates/update.apk");
                    try {
                        startActivityForResult(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, UpdateFile.MIME)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .putExtra(Intent.EXTRA_RETURN_RESULT, true), 40);
                    } catch (android.content.ActivityNotFoundException failure) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(release.download())));
                    }
                });
            } catch (java.io.IOException | RuntimeException error) {
                apk.delete();
                runOnUiThread(() -> {
                    updates.setEnabled(true);
                    updates.setText("Повторить обновление");
                    android.widget.Toast.makeText(this, "Не удалось скачать обновление",
                            android.widget.Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "apk-download").start();
    }
}
