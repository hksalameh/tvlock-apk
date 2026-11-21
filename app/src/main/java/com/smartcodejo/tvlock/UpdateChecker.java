package com.smartcodejo.tvlock;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Checks version.json on GitHub, downloads the new APK, and installs it on the parent's request. */
final class UpdateChecker {
    private static final String BASE = "https://raw.githubusercontent.com/hksalameh/tvlock-apk/main/";
    private static final long EVERY_MS = 6L * 60L * 60L * 1000L;
    private static volatile boolean busy;

    private UpdateChecker() {}

    static File apkFile(Context c) { return new File(c.getFilesDir(), "update.apk"); }

    static int currentCode(Context c) {
        try { return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode(); }
        catch (Exception e) { return 0; }
    }

    static void maybeCheck(Context ctx) {
        if (System.currentTimeMillis() - Prefs.updateLastCheck(ctx) < EVERY_MS) return;
        checkNow(ctx);
    }

    static void checkNow(Context ctx) {
        if (busy) return;
        final Context c = ctx.getApplicationContext();
        busy = true;
        new Thread(() -> {
            try {
                Prefs.setUpdateLastCheck(c, System.currentTimeMillis());
                JSONObject j = new JSONObject(fetch(BASE + "version.json").toString("UTF-8"));
                int code = j.getInt("versionCode");
                String name = j.optString("versionName", "?");
                if (code <= currentCode(c)) { Prefs.setUpdateReady(c, 0, ""); return; }
                if (Prefs.updateReadyCode(c) == code && apkFile(c).exists()) return;
                byte[] raw = Base64.decode(fetch(BASE + "TVLock.apk.b64").toString("US-ASCII").trim(), Base64.DEFAULT);
                if (raw.length < 20_000) return;
                try (OutputStream o = new FileOutputStream(apkFile(c))) { o.write(raw); }
                Prefs.setUpdateReady(c, code, name);
                EventLog.add(c, "⬇ تحديث جاهز: " + name + " (ثبّته من الإعدادات)");
                Notifier.send(c, "⬇ تحديث جاهز للتثبيت: " + name);
            } catch (Exception ignored) {
            } finally { busy = false; }
        }, "update-check").start();
    }

    private static ByteArrayOutputStream fetch(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(30000);
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b;
        } finally { h.disconnect(); }
    }

    /** Starts the system installer for the downloaded APK. Returns an error text or null. */
    static String install(Context c) {
        File f = apkFile(c);
        if (!f.exists() || Prefs.updateReadyCode(c) <= currentCode(c)) return "لا يوجد تحديث جاهز";
        try {
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int id = pi.createSession(sp);
            try (PackageInstaller.Session s = pi.openSession(id);
                 java.io.FileInputStream in = new java.io.FileInputStream(f);
                 OutputStream o = s.openWrite("tvlock", 0, f.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                s.fsync(o);
                Intent i = new Intent(c, UpdateInstallReceiver.class);
                PendingIntent p = PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                s.commit(p.getIntentSender());
            }
            EventLog.add(c, "بدء تثبيت التحديث");
            return null;
        } catch (Exception e) {
            return "فشل التثبيت: " + e.getMessage();
        }
    }
}
