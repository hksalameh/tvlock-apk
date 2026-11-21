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
    private static final int MAX_BODY = 16384;

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
        Prefs.migratePin(app);
        if (instance == null) {
            instance = new PhoneUnlockServer(app, () -> {
                Prefs.setForceLockUntil(app, 0L);
                ScreenTracker.restartSessionAt(app, System.currentTimeMillis());
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

    private static final class Resp { String msg; boolean ok; boolean good; }

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
        OutputStream out = c.getOutputStream();
        if (reqLine.startsWith("OPTIONS ")) { write(out, 200, "text/plain", "", false); return; }
        if (reqLine.startsWith("GET /panel")) { write(out, 200, "text/html", PanelPage.html(), false); return; }
        if (reqLine.startsWith("GET /ping")) { write(out, 200, "application/json", "{\"app\":\"tvlock\",\"name\":" + q(Prefs.tvName(ctx)) + "}", true); return; }
        if (!reqLine.startsWith("POST /")) {
            if (reqLine.startsWith("GET / ")) { write(out, 200, "text/html", page(null, status()), false); return; }
            write(out, 404, "text/html", page("غير موجود", ""), false);
            return;
        }
        contentLength = Math.min(Math.max(contentLength, 0), MAX_BODY);
        byte[] body = new byte[contentLength];
        int off = 0;
        while (off < contentLength) {
            int r = in.read(body, off, contentLength - off);
            if (r < 0) break;
            off += r;
        }
        Map<String, String> form = parseForm(new String(body, 0, off, StandardCharsets.UTF_8));
        boolean json = "1".equals(form.get("json"));
        String result = attempt(form.containsKey("pin") ? form.get("pin") : "");
        String msg;
        boolean unlockNow = false;
        String route = reqLine.split(" ")[1];
        if (!"ok".equals(result)) {
            msg = result;
        } else if (route.startsWith("/log")) {
            if (json) { write(out, 200, "application/json", "{\"ok\":true,\"msg\":" + q(EventLog.read(ctx)) + "}", true); }
            else write(out, 200, "text/html", logPage(EventLog.read(ctx)), false);
            return;
        } else if (route.startsWith("/backup")) {
            write(out, 200, "text/plain", Prefs.exportJson(ctx), json);
            return;
        } else {
            Resp r = new Resp();
            r.good = true;
            msg = route(route, form, r);
            unlockNow = r.ok;
        }
        boolean good = msg != null && msg.contains("✔");
        if (json) {
            write(out, 200, "application/json", "{\"ok\":" + good + ",\"msg\":" + q(msg == null ? "" : msg)
                    + ",\"status\":" + q(status()) + ",\"name\":" + q(Prefs.tvName(ctx)) + "}", true);
        } else {
            write(out, 200, "text/html", page(msg, status()), false);
        }
        if (unlockNow) listener.onUnlock();
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                default: b.append(ch < 32 ? ' ' : ch);
            }
        }
        return b.append('"').toString();
    }

    private static int[] hm(String t) {
        if (t == null || !t.matches("\\d{1,2}:\\d{2}")) return null;
        int a = Integer.parseInt(t.substring(0, t.indexOf(':')));
        int b = Integer.parseInt(t.substring(t.indexOf(':') + 1));
        return (a < 24 && b < 60) ? new int[]{a, b} : null;
    }

    private static int num(String s, int def, int min, int max) {
        try { int v = Integer.parseInt(s.trim()); return Math.max(min, Math.min(max, v)); }
        catch (Exception e) { return def; }
    }

    /** Authenticated commands. Returns a message (contains ✔ on success). */
    private String route(String route, Map<String, String> f, Resp r) {
        if (route.startsWith("/unlock")) {
            EventLog.add(ctx, "فتح من الموبايل");
            r.ok = true;
            return "تم الفتح ✔";
        }
        if (route.startsWith("/tonight")) {
            int[] t = hm(f.get("time"));
            if (t == null) return "وقت غير صالح";
            ScheduleUtil.setTonightStart(ctx, t[0], t[1]);
            EventLog.add(ctx, String.format(java.util.Locale.ROOT, "قفل الليلة فقط %02d:%02d من الموبايل", t[0], t[1]));
            return String.format(java.util.Locale.ROOT, "تم: قفل الليلة فقط الساعة %02d:%02d ✔", t[0], t[1]);
        }
        if (route.startsWith("/clear")) {
            ScheduleUtil.clearTonightStart(ctx);
            return "تم إلغاء تعديل الليلة ✔";
        }
        if (route.startsWith("/lock")) {
            int m = num(f.getOrDefault("min", "30"), 30, 1, 600);
            ScheduleUtil.lockFor(ctx, m);
            EventLog.add(ctx, "قفل فوري من الموبايل " + m + " دقيقة");
            return "تم القفل " + m + " دقيقة ✔";
        }
        if (route.startsWith("/bonus")) {
            String m = ScheduleUtil.bonus30(ctx);
            EventLog.add(ctx, "مكافأة 30 دقيقة من الموبايل");
            return m;
        }
        if (route.startsWith("/exam")) {
            boolean on = "1".equals(f.get("on"));
            int[] s = hm(f.get("start")), e = hm(f.get("end"));
            if (on && (s == null || e == null)) return "وقت غير صالح";
            if (s != null && e != null) Prefs.setExtra(ctx, on, s[0], s[1], e[0], e[1]);
            else Prefs.setExtraOn(ctx, on);
            ScheduleUtil.reschedule(ctx);
            LockOverlayService.sync(ctx);
            EventLog.add(ctx, "وضع الامتحانات " + (on ? "شغّال" : "موقوف"));
            return "وضع الامتحانات " + (on ? "شغّال" : "موقوف") + " ✔";
        }
        if (route.startsWith("/session")) {
            Prefs.setSessionOn(ctx, "1".equals(f.get("on")));
            Prefs.setSessionLimitMin(ctx, num(f.getOrDefault("limit", "120"), 120, 10, 600));
            Prefs.setSessionBreakMin(ctx, num(f.getOrDefault("brk", "15"), 15, 1, 120));
            return "تم حفظ حدّ الجلسة ✔";
        }
        if (route.startsWith("/tg")) {
            String tok = f.getOrDefault("token", "").trim(), chat = f.getOrDefault("chat", "").trim();
            String name = f.getOrDefault("name", "").trim();
            if (name.isEmpty()) name = Prefs.tvName(ctx);
            Prefs.setTelegram(ctx, tok, chat, name);
            if (!tok.isEmpty()) Notifier.send(ctx, "✅ تم ربط التنبيهات", true);
            return "تم حفظ إعدادات تيليجرام ✔";
        }
        if (route.startsWith("/restore")) {
            String d = f.getOrDefault("data", "");
            if (!Prefs.importJson(ctx, d)) return "ملف النسخة غير صالح";
            ScheduleUtil.reschedule(ctx);
            LockOverlayService.sync(ctx);
            EventLog.add(ctx, "استعادة نسخة احتياطية");
            return "تمت الاستعادة ✔";
        }
        return "غير موجود";
    }

    private String status() {
        LocalDate d = ScheduleUtil.tonightWindowDate(ctx);
        int[] st = ScheduleUtil.effectiveStart(ctx, d);
        boolean custom = d.toString().equals(Prefs.tonightDate(ctx));
        return String.format(java.util.Locale.ROOT, "القفل الليلة يبدأ %02d:%02d%s — الفتح %02d:%02d%s",
                st[0], st[1], custom ? " (معدّل لهذه الليلة فقط)" : "",
                ScheduleUtil.effectiveEnd(ctx, d)[0], ScheduleUtil.effectiveEnd(ctx, d)[1],
                ScheduleUtil.shouldLock(ctx) ? " — مقفول الآن" : "")
                + " | تشغيل اليوم: " + ScreenTracker.format(ScreenTracker.todayMs(ctx));
    }

    private synchronized String attempt(String pin) {
        long now = System.currentTimeMillis();
        if (now < lockedUntil) {
            EventLog.add(ctx, "محاولة من الموبايل أثناء الإيقاف المؤقت");
            return "محاولات كثيرة — انتظر " + ((lockedUntil - now) / 1000 + 1) + " ثانية";
        }
        boolean good = Prefs.hasPin(ctx) && pin.length() > 0 && Prefs.checkPin(ctx, pin);
        if (good) {
            fails = 0;
            return "ok";
        }
        if (++fails >= MAX_FAILS) {
            fails = 0;
            lockedUntil = now + LOCKOUT_MS;
            EventLog.alert(ctx, "⚠ 5 محاولات PIN غلط من الموبايل — إيقاف 5 دقائق");
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
                String k = kv.substring(0, i);
                int lim = k.equals("data") ? 12000 : (k.equals("token") ? 128 : 64);
                m.put(k, v.length() > lim ? v.substring(0, lim) : v);
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

    private static void write(OutputStream out, int code, String type, String text, boolean cors) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") + "\r\n"
                + "Content-Type: " + type + "; charset=utf-8\r\n"
                + "Cache-Control: no-store\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type\r\n"
                + "Access-Control-Allow-Private-Network: true\r\n"
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
                + "placeholder='PIN' style='font-size:22px;width:100%;padding:8px;text-align:center;box-sizing:border-box;margin-top:8px'>";
        String btn = "style='margin-top:8px;font-size:19px;width:100%;padding:9px'";
        String in = "style='font-size:20px;width:100%;padding:8px;text-align:center;box-sizing:border-box;margin-top:6px'";
        StringBuilder b = new StringBuilder();
        b.append("<form method='POST' action='/unlock'><h3>🔓 فتح الآن</h3>").append(pin).append("<button ").append(btn).append(">فتح</button></form>");
        b.append("<form method='POST' action='/bonus'><h3>⏱ +30 دقيقة</h3>").append(pin).append("<button ").append(btn).append(">أضف 30 دقيقة</button></form>");
        b.append("<form method='POST' action='/lock'><h3>🔒 قفل فوري</h3><select name='min' ").append(in).append("><option value='30'>30 دقيقة</option><option value='60'>60 دقيقة</option><option value='120'>120 دقيقة</option></select>")
                .append(pin).append("<button ").append(btn).append(">اقفل الآن</button></form>");
        b.append("<form method='POST' action='/tonight'><h3>🌙 قفل الليلة فقط</h3><input name='time' type='time' value='02:30' ").append(in).append(">")
                .append(pin).append("<button ").append(btn).append(">تعيين لهذه الليلة فقط</button></form>");
        b.append("<form method='POST' action='/clear'>").append(pin).append("<button ").append(btn).append(">إلغاء تعديل الليلة</button></form>");
        b.append("<form method='POST' action='/exam'><h3>📚 وضع الامتحانات</h3><select name='on' ").append(in).append("><option value='1'>شغّال</option><option value='0'>موقوف</option></select>")
                .append("<input name='start' type='time' value='16:00' ").append(in).append("><input name='end' type='time' value='20:00' ").append(in).append(">")
                .append(pin).append("<button ").append(btn).append(">حفظ</button></form>");
        b.append("<form method='POST' action='/session'><h3>⏸ حدّ الجلسة</h3><select name='on' ").append(in).append("><option value='1'>شغّال</option><option value='0'>موقوف</option></select>")
                .append("<input name='limit' type='number' value='120' placeholder='دقائق التشغيل' ").append(in).append("><input name='brk' type='number' value='15' placeholder='دقائق الاستراحة' ").append(in).append(">")
                .append(pin).append("<button ").append(btn).append(">حفظ</button></form>");
        b.append("<form method='POST' action='/tg'><h3>📨 تنبيهات تيليجرام</h3><input name='name' placeholder='اسم التلفزيون' ").append(in).append(">")
                .append("<input name='token' placeholder='Bot token' ").append(in).append("><input name='chat' placeholder='Chat id' ").append(in).append(">")
                .append(pin).append("<button ").append(btn).append(">حفظ وتجربة</button></form>");
        b.append("<form method='POST' action='/log'><h3>📋 السجل</h3>").append(pin).append("<button ").append(btn).append(">عرض</button></form>");
        b.append("<form method='POST' action='/backup'><h3>💾 نسخة احتياطية</h3>").append(pin).append("<button ").append(btn).append(">تنزيل النسخة</button></form>");
        b.append("<form method='POST' action='/restore'><h3>♻ استعادة</h3><textarea name='data' rows='3' placeholder='الصق النسخة هنا' ").append(in).append("></textarea>")
                .append(pin).append("<button ").append(btn).append(">استعادة</button></form>");
        b.append("<p><a style='color:#9cf' href='/panel'>لوحة التحكم بتلفزيونين</a></p>");
        return "<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>TV Lock</title></head>"
                + "<body style='font-family:sans-serif;background:#031230;color:#fff;max-width:380px;"
                + "margin:30px auto;padding:0 16px;text-align:center'>"
                + "<h2>TV Lock</h2>" + m + st + b + "</body></html>";
    }
}
