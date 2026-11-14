package ru.artemzozula.mobileindicator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

public final class MonitorService extends Service {
    static final String CHANNEL = "mobile_status";
    private static final int NOTIFICATION_ID = 1;
    private static final int TIMEOUT_MS = 3000;
    private static final long INTERVAL_MS = 30_000;

    static final String ACTION_REFRESH = "refresh";
    static final String ACTION_MODE = "mode";
    static Snapshot snapshot;

    record Snapshot(String transport, String status, int icon, long checkedAt,
                    long externalMs, long yandexMs, boolean confirming, String standard, int dbm) {}

    private enum Status {
        NONE(R.drawable.ic_none, "Доступ не подтверждён"),
        LIMITED(R.drawable.ic_limited, "Вероятно, белый список"),
        FULL(R.drawable.ic_full, "Внешний доступ есть"),
        CHECKING(R.drawable.ic_checking, "Проверка интернета");

        final int icon;
        final String title;

        Status(int icon, String title) {
            this.icon = icon;
            this.title = title;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable poll = this::poll;
    private ConnectivityManager connectivity;
    private NotificationManager notifications;
    private HistoryStore history;
    private final RadioInfo radio = new RadioInfo();
    private Network selected;
    static Network probeNetwork;

    private boolean mobileOnly;
    private boolean registered;
    private String transport = "Нет подключения";
    private int generation;
    private boolean runningProbe;
    private boolean destroyed;
    private Status confirmation;
    private Status shown = Status.CHECKING;

    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) {
            select(network);
        }

        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            if (network.equals(selected)) updateTransport(capabilities);
        }

        @Override public void onLost(Network network) {
            if (network.equals(selected)) select(null);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        history = new HistoryStore(this);
        notifications = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Состояние мобильного интернета", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        notifications.createNotificationChannel(channel);
        startForeground(NOTIFICATION_ID, notification(shown));

        connectivity = getSystemService(ConnectivityManager.class);
        configure();
    }

    private void configure() {
        if (registered) connectivity.unregisterNetworkCallback(callback);
        mobileOnly = BootReceiver.mobileOnly(this);
        history.separate();
        select(null);
        if (!mobileOnly) {
            connectivity.registerDefaultNetworkCallback(callback, main);
            registered = true;
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        connectivity.requestNetwork(request, callback, main);
        registered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (BootReceiver.enabled(this)) {
            String action = intent == null ? null : intent.getAction();
            if (ACTION_MODE.equals(action) && mobileOnly != BootReceiver.mobileOnly(this)) configure();
            if (ACTION_REFRESH.equals(action)) schedule(0);
            return START_STICKY;
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private void select(Network network) {
        selected = network;
        probeNetwork = network;
        generation++;
        confirmation = null;
        transport = network == null ? "Нет подключения" : "Определение сети…";
        shown = network == null ? Status.NONE : Status.CHECKING;
        publish(0, -1, -1);
        schedule(network == null ? 2500 : 0);
    }

    private void updateTransport(NetworkCapabilities caps) {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transport = "VPN";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "Wi-Fi";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "Мобильная сеть";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "Ethernet";
        else transport = "Другая сеть";
        publish(snapshot.checkedAt(), snapshot.externalMs(), snapshot.yandexMs(), snapshot.standard(), snapshot.dbm());
    }

    private void schedule(long delay) {
        main.removeCallbacks(poll);
        main.postDelayed(poll, delay);
    }

    private void poll() {
        Network network = selected;
        if (network == null) {
            history.observe(System.currentTimeMillis(), HistoryStore.NONE,
                    mobileOnly ? "Мобильная сеть" : "Нет подключения");
            schedule(INTERVAL_MS);
            return;
        }
        if (runningProbe) return;
        int probeGeneration = generation;
        runningProbe = true;
        worker.execute(() -> {
            long started = SystemClock.elapsedRealtime();
            long externalMs = outsideReachable(network) ? SystemClock.elapsedRealtime() - started : -1;
            started = SystemClock.elapsedRealtime();
            long yandexMs = yandexReachable(network) ? SystemClock.elapsedRealtime() - started : -1;
            Status measured = externalMs >= 0 ? Status.FULL
                    : yandexMs >= 0 ? Status.LIMITED : Status.NONE;
            main.post(() -> {
                runningProbe = false;
                if (destroyed) return;
                if (probeGeneration != generation) {
                    if (selected != null) schedule(0);
                    return;
                }
                if (shown == Status.CHECKING || measured.ordinal() >= shown.ordinal()
                        || measured == confirmation) {
                    shown = measured;
                    confirmation = null;
                } else {
                    confirmation = measured;
                }
                long now = System.currentTimeMillis();
                String standard = "Мобильная сеть".equals(transport) ? radio.read(this) : "";
                int dbm = standard.isEmpty() ? Integer.MAX_VALUE : radio.strength();
                if (confirmation == null) {
                    String state = shown == Status.FULL ? HistoryStore.FULL
                            : shown == Status.LIMITED ? HistoryStore.LIMITED : HistoryStore.NONE;
                    history.observe(now, state, standard.isEmpty() ? transport : standard, dbm);
                }
                publish(now, externalMs, yandexMs, standard, dbm);
                schedule(INTERVAL_MS);
            });
        });
    }

    private static boolean outsideReachable(Network network) {
        try (Socket socket = network.getSocketFactory().createSocket()) {
            InetAddress google = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
            socket.connect(new InetSocketAddress(google, 53), TIMEOUT_MS);
            return true;
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    private static boolean yandexReachable(Network network) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) network.openConnection(new URL("https://ya.ru/"));
            connection.setRequestMethod("HEAD");
            connection.setUseCaches(false);
            connection.setRequestProperty("Connection", "close");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int code = connection.getResponseCode();
            return code >= 200 && code < 500;
        } catch (IOException | SecurityException exception) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void publish(long checkedAt, long externalMs, long yandexMs) {
        publish(checkedAt, externalMs, yandexMs, "", Integer.MAX_VALUE);
    }

    private void publish(long checkedAt, long externalMs, long yandexMs, String standard, int dbm) {
        snapshot = new Snapshot(transport, shown.title, shown.icon, checkedAt, externalMs,
                yandexMs, confirmation != null, standard, dbm);
        notifications.notify(NOTIFICATION_ID, notification(shown));
    }

    private Notification notification(Status status) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String detail;
        switch (status) {
            case FULL: detail = "Доступен 8.8.8.8:53"; break;
            case LIMITED: detail = "Яндекс доступен, 8.8.8.8:53 не отвечает"; break;
            case NONE: detail = selected == null ? "Нет подключения"
                    : "Яндекс и 8.8.8.8:53 не ответили"; break;
            default: detail = "Ожидание результатов";
        }
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(status.icon)
                .setContentTitle(status.title)
                .setContentText(transport + " · " + detail)
                .setContentIntent(open)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    @Override public void onDestroy() {
        destroyed = true;
        snapshot = null;
        probeNetwork = null;
        if (registered) connectivity.unregisterNetworkCallback(callback);
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        radio.close();
        history.close();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
