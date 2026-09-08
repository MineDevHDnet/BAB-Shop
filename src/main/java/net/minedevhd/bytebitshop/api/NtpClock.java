package net.minedevhd.bytebitshop.api;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;

public final class NtpClock {
    private volatile long offsetMs;

    public long now() {
        return System.currentTimeMillis() + offsetMs;
    }

    public void syncAsync() {
        CompletableFuture.runAsync(() -> {
            String[] servers = {"de.pool.ntp.org", "pool.ntp.org"};
            for (String server : servers) {
                try {
                    offsetMs = requestOffset(server);
                    return;
                } catch (Exception ignored) {}
            }
        });
    }

    private static long requestOffset(String host) throws Exception {
        byte[] buffer = new byte[48];
        buffer[0] = 0x1B;

        DatagramSocket socket = new DatagramSocket();
        try {
            socket.setSoTimeout(1500);
            InetAddress address = InetAddress.getByName(host);
            long send = System.currentTimeMillis();
            writeTimestamp(buffer, 40, send);
            socket.send(new DatagramPacket(buffer, buffer.length, address, 123));

            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            long receive = System.currentTimeMillis();
            long serverTransmit = readTimestamp(buffer, 40);
            long midpoint = send + ((receive - send) / 2L);
            return serverTransmit - midpoint;
        } finally {
            socket.close();
        }
    }

    private static void writeTimestamp(byte[] data, int offset, long timeMs) {
        double seconds = timeMs / 1000.0 + 2208988800.0;
        long whole = (long) seconds;
        long fraction = (long) ((seconds - whole) * 4294967296.0);
        for (int i = 0; i < 4; i++) data[offset + i] = (byte) (whole >>> (24 - i * 8));
        for (int i = 0; i < 4; i++) data[offset + 4 + i] = (byte) (fraction >>> (24 - i * 8));
    }

    private static long readTimestamp(byte[] data, int offset) {
        long seconds = 0;
        long fraction = 0;
        for (int i = 0; i < 4; i++) seconds = (seconds << 8) | (data[offset + i] & 0xFFL);
        for (int i = 0; i < 4; i++) fraction = (fraction << 8) | (data[offset + 4 + i] & 0xFFL);
        double unixSeconds = seconds - 2208988800.0 + (fraction / 4294967296.0);
        return (long) (unixSeconds * 1000.0);
    }
}
