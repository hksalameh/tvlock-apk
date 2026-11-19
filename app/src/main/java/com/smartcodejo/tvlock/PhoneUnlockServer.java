package com.smartcodejo.tvlock;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Tiny HTTP server (home LAN) so the PIN can be typed on a phone instead of
 * on the TV screen, where bystanders can see the focused buttons.
 * GET  /        -> PIN form
 * POST /unlock  -> pin=XXXX
 * After MAX_FAILS wrong PINs the server refuses attempts for LOCKOUT_MS.
 */
final class PhoneUnlockServer {
    interface Listener { void onUnlock(); }

    static final int PORT = 8765;
    private static final int MAX_FAILS = 5;
    private static final long LOCKOUT_MS = 5 * 60 * 1000L;
    private static final int MAX_BODY = 256;

    private final Context ctx;
    private final Listener listener;
    private volatile ServerSocket server;
    private volatile boolean running;
    private final java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newFixedThreadPool(6);
    private int fails;
    private long lockedUntil;

    PhoneUnlockServer(Context c, Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
    }

    synchronized void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(this::loop, "tvlock-phone-unlock");
        t.setDaemon(true);
        t.start();
    }

    synchronized void stop() {
        running = false;
        ServerSocket s = server;
        server = null;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private void loop() {
        try {
            ServerSocket s = new ServerSocket(PORT);
            server = s;
            while (running) {
                try {
                    final Socket c = s.accept();
                    pool.execute(() -> {
                        try {
                            c.setSoTimeout(5000);
                            handle(c);
                            try { c.shutdownOutput(); } catch (IOException ignored) {}
                        } catch (IOException ignored) {
                        } finally {
                            try { c.close(); } catch (IOException ignored) {}
                        }
                    });
                } catch (IOException e) {
                    if (!running) break;
                }
            }
            pool.shutdownNow();
        } catch (IOException ignored) {
            running = false;
        }
    }

    private void handle(Socket c) throws IOException {
        InputStream in = c.getInputStream();
        String reqLine = readLine(in);
        if (reqLine == null) return;
        int contentLength = 0;
        String h;
        while ((h = readLine(in)) != null && !h.isEmpty()) {
            if (h.toLowerCase().startsWith("content-length:")) {
                try { contentLength = Integer.parseInt(h.substring(15).trim()); }
                catch (NumberFormatException ignored) {}
            }
        }
        String msg = null;
        boolean ok = false;
        if (reqLine.startsWith("POST /unlock")) {
            contentLength = Math.min(Math.max(contentLength, 0), MAX_BODY);
            byte[] body = new byte[contentLength];
            int off = 0;
            while (off < contentLength) {
                int r = in.read(body, off, contentLength - off);
                if (r < 0) break;
                off += r;
            }
            String pin = parsePin(new String(body, 0, off, StandardCharsets.UTF_8));
            String result = attempt(pin);
            if ("ok".equals(result)) ok = true;
            else msg = result;
        } else if (!reqLine.startsWith("GET / ")) {
            write(c.getOutputStream(), 404, page("غير موجود", false));
            return;
        }
        write(c.getOutputStream(), 200, page(msg, ok));
        if (ok) listener.onUnlock();
    }

    private synchronized String attempt(String pin) {
        long now = System.currentTimeMillis();
        if (now < lockedUntil) {
            return "محاولات كثيرة — انتظر " + ((lockedUntil - now) / 1000 + 1) + " ثانية";
        }
        String real = Prefs.pin(ctx);
        boolean good = !real.isEmpty() && pin.length() > 0 && MessageDigest.isEqual(
                real.getBytes(StandardCharsets.UTF_8), pin.getBytes(StandardCharsets.UTF_8));
        if (good) {
            fails = 0;
            return "ok";
        }
        if (++fails >= MAX_FAILS) {
            fails = 0;
            lockedUntil = now + LOCKOUT_MS;
            return "PIN غير صحيح — تم إيقاف المحاولات 5 دقائق";
        }
        return "PIN غير صحيح";
    }

    private static String parsePin(String body) {
        for (String kv : body.split("&")) {
            if (kv.startsWith("pin=")) {
                try {
                    String v = URLDecoder.decode(kv.substring(4), "UTF-8");
                    return v.length() > 16 ? v.substring(0, 16) : v;
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') break;
            if (ch != '\r' && b.size() < 2048) b.write(ch);
        }
        if (ch == -1 && b.size() == 0) return null;
        return b.toString("UTF-8");
    }

    private static void write(OutputStream out, int code, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") + "\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Cache-Control: no-store\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String page(String msg, boolean ok) {
        String status = ok ? "<p style='color:#6f6'>تم الفتح ✔</p>"
                : (msg != null ? "<p style='color:#f88'>" + msg + "</p>" : "");
        String form = ok ? "" :
                "<form method='POST' action='/unlock'>"
                        + "<input name='pin' type='password' inputmode='numeric' autocomplete='off' "
                        + "autofocus placeholder='PIN' style='font-size:28px;width:100%;padding:12px;"
                        + "text-align:center;box-sizing:border-box'>"
                        + "<button style='margin-top:14px;font-size:24px;width:100%;padding:12px'>فتح</button>"
                        + "</form>";
        return "<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>فتح التلفزيون</title></head>"
                + "<body style='font-family:sans-serif;background:#031230;color:#fff;max-width:360px;"
                + "margin:40px auto;padding:0 16px;text-align:center'>"
                + "<h2>🔒 فتح التلفزيون</h2>" + status + form + "</body></html>";
    }
}
