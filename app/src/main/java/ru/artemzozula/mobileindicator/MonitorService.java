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

    private enum Status {
        NONE(R.drawable.ic_none, "Мобильный интернет недоступен"),
        LIMITED(R.drawable.ic_limited, "Вероятно, белый список"),
        FULL(R.drawable.ic_full, "Мобильный интернет работает"),
        CHECKING(R.drawable.ic_checking, "Проверка мобильного интернета");

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
    private Network mobile;
    private int generation;
    private boolean runningProbe;
    private boolean destroyed;
    private Status confirmation;
    private Status shown = Status.CHECKING;

    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) {
            mobile = network;
            generation++;
            confirmation = null;
            display(Status.CHECKING);
            schedule(0);
        }

        @Override public void onLost(Network network) {
            if (!network.equals(mobile)) return;
            mobile = null;
            generation++;
            confirmation = null;
            display(Status.NONE);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        notifications = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Состояние мобильного интернета", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        notifications.createNotificationChannel(channel);
        startForeground(NOTIFICATION_ID, notification(shown));

        connectivity = getSystemService(ConnectivityManager.class);
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        connectivity.requestNetwork(request, callback, main);
        schedule(2500);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (BootReceiver.enabled(this)) return START_STICKY;
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private void schedule(long delay) {
        main.removeCallbacks(poll);
        main.postDelayed(poll, delay);
    }

    private void poll() {
        Network network = mobile;
        if (network == null) {
            display(Status.NONE);
            return;
        }
        if (runningProbe) return;
        int probeGeneration = generation;
        runningProbe = true;
        worker.execute(() -> {
            Status measured = outsideReachable(network) ? Status.FULL
                    : yandexReachable(network) ? Status.LIMITED : Status.NONE;
            main.post(() -> {
                runningProbe = false;
                if (destroyed) return;
                if (probeGeneration != generation) {
                    if (mobile != null) schedule(0);
                    return;
                }
                if (shown == Status.CHECKING || measured.ordinal() >= shown.ordinal()
                        || measured == confirmation) {
                    display(measured);
                    confirmation = null;
                } else {
                    confirmation = measured;
                }
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

    private void display(Status status) {
        if (destroyed || shown == status) return;
        shown = status;
        notifications.notify(NOTIFICATION_ID, notification(status));
    }

    private Notification notification(Status status) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String detail;
        switch (status) {
            case FULL: detail = "Доступен 8.8.8.8:53"; break;
            case LIMITED: detail = "Яндекс доступен, 8.8.8.8:53 не отвечает"; break;
            case NONE: detail = mobile == null ? "Нет подключения по мобильной сети"
                    : "Яндекс и 8.8.8.8:53 не ответили"; break;
            default: detail = "Проверка через мобильную сеть";
        }
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(status.icon)
                .setContentTitle(status.title)
                .setContentText(detail)
                .setContentIntent(open)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    @Override public void onDestroy() {
        destroyed = true;
        connectivity.unregisterNetworkCallback(callback);
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
