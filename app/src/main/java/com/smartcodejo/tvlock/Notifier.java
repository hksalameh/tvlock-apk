package com.smartcodejo.tvlock;

import android.content.Context;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Sends short alerts to a Telegram bot (token/chat are configured from the phone page). */
final class Notifier {
    private static final long MIN_GAP_MS = 60_000L;
    private static final Map<String, Long> last = new HashMap<>();

    private Notifier() {}

    static boolean configured(Context c) {
        return !Prefs.tgToken(c).isEmpty() && !Prefs.tgChat(c).isEmpty();
    }

    static void send(Context ctx, String text) {
        send(ctx, text, false);
    }

    static void send(Context ctx, String text, boolean force) {
        final Context c = ctx.getApplicationContext();
        if (!configured(c)) return;
        synchronized (last) {
            long now = System.currentTimeMillis();
            Long prev = last.get(text);
            if (!force && prev != null && now - prev < MIN_GAP_MS) return;
            last.put(text, now);
        }
        final String token = Prefs.tgToken(c), chat = Prefs.tgChat(c);
        final String msg = "📺 " + Prefs.tvName(c) + "\n" + text;
        new Thread(() -> {
            try {
                URL u = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
                HttpURLConnection h = (HttpURLConnection) u.openConnection();
                h.setConnectTimeout(8000);
                h.setReadTimeout(8000);
                h.setRequestMethod("POST");
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                String body = "chat_id=" + URLEncoder.encode(chat, "UTF-8") + "&text=" + URLEncoder.encode(msg, "UTF-8");
                try (OutputStream o = h.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
                h.getResponseCode();
                h.disconnect();
            } catch (Exception ignored) {
            }
        }, "tg-notify").start();
    }
}
