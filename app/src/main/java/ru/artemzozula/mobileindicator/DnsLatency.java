package ru.artemzozula.mobileindicator;

import android.net.Network;
import android.os.SystemClock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

final class DnsLatency {
    private static final int SAMPLES = 3;

    static String measure(Network network) {
        long[] times = new long[SAMPLES];
        int received = 0;
        for (int i = 0; i < SAMPLES; i++) {
            try {
                times[received] = sample(network);
                received++;
            } catch (IOException | SecurityException ignored) {
                // Count the missing reply without treating it as a connectivity verdict.
            }
        }
        if (received == 0) return "Нет ответов · 0/3";
        Arrays.sort(times, 0, received);
        long median = (times[(received - 1) / 2] + times[received / 2]) / 2;
        return median + " мс · " + received + "/3 ответов";
    }

    private static long sample(Network network) throws IOException {
        byte[] query = {0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
                7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', 3, 'c', 'o', 'm', 0, 0, 1, 0, 1};
        int id = ThreadLocalRandom.current().nextInt(65536);
        query[0] = (byte) (id >> 8);
        query[1] = (byte) id;
        try (DatagramSocket socket = new DatagramSocket()) {
            network.bindSocket(socket);
            socket.connect(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}), 53);
            socket.setSoTimeout(1000);
            byte[] response = new byte[512];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            long start = SystemClock.elapsedRealtime();
            socket.send(new DatagramPacket(query, query.length));
            socket.receive(packet);
            if (packet.getLength() < 12 || response[0] != query[0] || response[1] != query[1]
                    || (response[2] & 0x80) == 0) throw new IOException("Unexpected DNS response");
            return SystemClock.elapsedRealtime() - start;
        }
    }
}
