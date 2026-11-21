package com.smartcodejo.tvlock;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Small rolling log (last 200 events) of security-relevant events. Newest line is last in the file. */
final class EventLog {
    private static final int MAX_LINES = 200;
    private static final long DEDUPE_MS = 10_000L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static String lastMsg = "";
    private static long lastAt = 0L;

    private EventLog() {}

    static synchronized void add(Context c, String msg) {
        try {
            long now = System.currentTimeMillis();
            if (msg.equals(lastMsg) && now - lastAt < DEDUPE_MS) return;
            lastMsg = msg;
            lastAt = now;
            File f = new File(c.getApplicationContext().getFilesDir(), "events.log");
            List<String> lines = new ArrayList<>();
            if (f.exists()) lines.addAll(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
            lines.add(ZonedDateTime.now(ZoneOffset.ofHours(3)).format(FMT) + "  " + msg);
            while (lines.size() > MAX_LINES) lines.remove(0);
            try (FileOutputStream out = new FileOutputStream(f, false)) {
                out.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    /** Logs the event and also sends it to Telegram (if configured). */
    static void alert(Context c, String msg) {
        add(c, msg);
        Notifier.send(c, msg);
    }

    /** Newest first. */
    static synchronized String read(Context c) {
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), "events.log");
            if (!f.exists()) return "لا يوجد أحداث بعد";
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            StringBuilder b = new StringBuilder();
            for (int i = lines.size() - 1; i >= 0; i--) b.append(lines.get(i)).append('\n');
            return b.length() == 0 ? "لا يوجد أحداث بعد" : b.toString();
        } catch (Exception e) {
            return "تعذر قراءة السجل";
        }
    }
}
