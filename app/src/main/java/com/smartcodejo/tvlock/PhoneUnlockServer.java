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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

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

    private static PhoneUnlockServer instance;

    /** One always-on server for the whole app (hosted by the accessibility service). Safe to call repeatedly. */
    static synchronized void ensureStarted(Context c) {
        final Context app = c.getApplicationContext();
        if (instance == null) {
            instance = new PhoneUnlockServer(app, () -> {
                if (ScheduleUtil.shouldLock(app)) {
                    Prefs.setBypassUntil(app, ScheduleUtil.currentWindowEnd(app));
                }
                LockOverlayService.sync(app);
            });
        }
        instance.start();
    }

    static synchronized void stopInstance() {
        if (instance != null) { instance.stop(); instance = null; }
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
        if (reqLine.startsWith("POST /")) {
            contentLength = Math.min(Math.max(contentLength, 0), MAX_BODY);
            byte[] body = new byte[contentLength];
            int off = 0;
            while (off < contentLength) {
                int r = in.read(body, off, contentLength - off);
                if (r < 0) break;
                off += r;
            }
            Map<String, String> form = parseForm(new String(body, 0, off, StandardCharsets.UTF_8));
            String result = attempt(form.containsKey("pin") ? form.get("pin") : "");
            if (!"ok".equals(result)) {
                msg = result;
            } else if (reqLine.startsWith("POST /log")) {
                write(c.getOutputStream(), 200, logPage(EventLog.read(ctx)));
                return;
            } else if (reqLine.startsWith("POST /unlock")) {
                EventLog.add(ctx, "فتح من الموبايل");
                ok = true;
                msg = "تم الفتح ✔";
            } else if (reqLine.startsWith("POST /tonight")) {
                String t = form.containsKey("time") ? form.get("time") : "";
                if (t.matches("\\d{1,2}:\\d{2}")) {
                    int th = Integer.parseInt(t.substring(0, t.indexOf(':')));
                    int tm = Integer.parseInt(t.substring(t.indexOf(':') + 1));
                    if (th < 24 && tm < 60) {
                        ScheduleUtil.setTonightStart(ctx, th, tm);
                        EventLog.add(ctx, String.format(java.util.Locale.ROOT, "قفل الليلة فقط %02d:%02d من الموبايل", th, tm));
                        msg = String.format(java.util.Locale.ROOT, "تم: قفل الليلة فقط الساعة %02d:%02d ✔", th, tm);
                    } else msg = "وقت غير صالح";
                } else msg = "وقت غير صالح";
            } else if (reqLine.startsWith("POST /clear")) {
                ScheduleUtil.clearTonightStart(ctx);
                msg = "تم إلغاء تعديل الليلة ✔";
            } else {
                write(c.getOutputStream(), 404, page("غير موجود", ""));
                return;
            }
        } else if (!reqLine.startsWith("GET / ")) {
            write(c.getOutputStream(), 404, page("غير موجود", ""));
            return;
        }
        write(c.getOutputStream(), 200, page(msg, status()));
        if (ok) listener.onUnlock();
    }

    private String status() {
        LocalDate d = ScheduleUtil.tonightWindowDate(ctx);
        int[] st = ScheduleUtil.effectiveStart(ctx, d);
        boolean custom = d.toString().equals(Prefs.tonightDate(ctx));
        return String.format(java.util.Locale.ROOT, "القفل الليلة يبدأ %02d:%02d%s — الفتح %02d:%02d%s",
                st[0], st[1], custom ? " (معدّل لهذه الليلة فقط)" : "",
                ScheduleUtil.effectiveEnd(ctx, d)[0], ScheduleUtil.effectiveEnd(ctx, d)[1],
                ScheduleUtil.shouldLock(ctx) ? " — مقفول الآن" : "");
    }

    private synchronized String attempt(String pin) {
        long now = System.currentTimeMillis();
        if (now < lockedUntil) {
            EventLog.add(ctx, "محاولة من الموبايل أثناء الإيقاف المؤقت");
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
            EventLog.add(ctx, "5 محاولات PIN غلط من الموبايل — إيقاف 5 دقائق");
            return "PIN غير صحيح — تم إيقاف المحاولات 5 دقائق";
        }
        EventLog.add(ctx, "PIN غلط من الموبايل");
        return "PIN غير صحيح";
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> m = new HashMap<>();
        for (String kv : body.split("&")) {
            int i = kv.indexOf('=');
            if (i <= 0) continue;
            try {
                String v = URLDecoder.decode(kv.substring(i + 1), "UTF-8");
                m.put(kv.substring(0, i), v.length() > 16 ? v.substring(0, 16) : v);
            } catch (Exception ignored) {}
        }
        return m;
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

    private static String logPage(String log) {
        String safe = log.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>سجل TV Lock</title></head>"
                + "<body style='font-family:sans-serif;background:#031230;color:#fff;margin:16px'>"
                + "<h3>📋 السجل (الأحدث أولاً)</h3><pre style='white-space:pre-wrap;font-size:14px;direction:rtl'>" + safe
                + "</pre><p><a style='color:#9cf' href='/'>رجوع</a></p></body></html>";
    }

    private static String page(String msg, String status) {
        String m = msg == null ? "" : "<p style='color:" + (msg.contains("✔") ? "#8f8" : "#f88") + "'>" + msg + "</p>";
        String st = status.isEmpty() ? "" : "<p style='color:#9cf;font-size:15px'>" + status + "</p>";
        String pin = "<input name='pin' type='password' inputmode='numeric' autocomplete='off' "
                + "placeholder='PIN' style='font-size:24px;width:100%;padding:10px;text-align:center;box-sizing:border-box;margin-top:8px'>";
        String btn = "style='margin-top:10px;font-size:20px;width:100%;padding:10px'";
        String unlock = "<form method='POST' action='/unlock'><h3>🔓 فتح الآن</h3>" + pin
                + "<button " + btn + ">فتح</button></form>";
        String tonight = "<form method='POST' action='/tonight'><h3>🌙 قفل الليلة فقط</h3>"
                + "<input name='time' type='time' value='02:30' style='font-size:24px;width:100%;padding:10px;text-align:center;box-sizing:border-box'>"
                + pin + "<button " + btn + ">تعيين لهذه الليلة فقط</button></form>"
                + "<form method='POST' action='/log'><h3>📋 السجل</h3>" + pin + "<button " + btn + ">عرض السجل</button></form>"
                + "<form method='POST' action='/clear'>" + pin + "<button " + btn + ">إلغاء تعديل الليلة</button></form>";
        return "<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>TV Lock</title></head>"
                + "<body style='font-family:sans-serif;background:#031230;color:#fff;max-width:380px;"
                + "margin:30px auto;padding:0 16px;text-align:center'>"
                + "<h2>TV Lock</h2>" + m + st + unlock + "<hr style='opacity:.3;margin:24px 0'>" + tonight
                + "</body></html>";
    }
}
