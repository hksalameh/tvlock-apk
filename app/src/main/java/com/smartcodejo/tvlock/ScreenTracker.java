package com.smartcodejo.tvlock;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Tracks how long the TV screen has been on (today's total and the current continuous session). */
final class ScreenTracker {
    private static final String NAME = "tv_lock_screen";

    private ScreenTracker() {}

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    private static String today() { return LocalDate.now(ZoneOffset.ofHours(3)).toString(); }

    private static void rollDay(SharedPreferences sp, long now) {
        if (!today().equals(sp.getString("scr_day", ""))) {
            long since = sp.getLong("scr_since", 0L);
            sp.edit().putString("scr_day", today()).putLong("scr_ms", 0L)
                    .putLong("scr_since", since > 0 ? now : 0L).apply();
        }
    }

    static synchronized void onScreen(Context c, boolean on) {
        SharedPreferences sp = sp(c);
        long now = System.currentTimeMillis();
        rollDay(sp, now);
        long since = sp.getLong("scr_since", 0L);
        if (on) {
            if (since == 0L) sp.edit().putLong("scr_since", now).apply();
        } else if (since > 0L) {
            long add = Math.max(0L, now - since);
            sp.edit().putLong("scr_ms", sp.getLong("scr_ms", 0L) + add).putLong("scr_since", 0L).apply();
        }
    }

    static synchronized long todayMs(Context c) {
        SharedPreferences sp = sp(c);
        long now = System.currentTimeMillis();
        rollDay(sp, now);
        long since = sp.getLong("scr_since", 0L);
        return sp.getLong("scr_ms", 0L) + (since > 0L ? Math.max(0L, now - since) : 0L);
    }

    static synchronized long sessionMs(Context c) {
        long since = sp(c).getLong("scr_since", 0L);
        return since > 0L ? Math.max(0L, System.currentTimeMillis() - since) : 0L;
    }

    /** Closes the running session and restarts the counter at restartAt (used after a forced break). */
    static synchronized void restartSessionAt(Context c, long restartAt) {
        SharedPreferences sp = sp(c);
        long now = System.currentTimeMillis();
        rollDay(sp, now);
        long since = sp.getLong("scr_since", 0L);
        if (since > 0L) {
            sp.edit().putLong("scr_ms", sp.getLong("scr_ms", 0L) + Math.max(0L, now - since)).apply();
        }
        sp.edit().putLong("scr_since", restartAt).apply();
    }

    static String format(long ms) {
        long m = ms / 60000L;
        return (m / 60) + " ساعة و" + (m % 60) + " دقيقة";
    }
}
