package com.jizhang.jizhangben;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class CaptureStore {
    private static final String PREFS = "wechat_capture";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_QUEUE = "queue";
    private static final String KEY_DIAG = "diag";
    private static final int MAX_QUEUE = 300;

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static synchronized boolean append(Context context, JSONObject record) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray queue = load(prefs);
        for (int i = 0; i < queue.length(); i++) {
            try {
                JSONObject old = queue.getJSONObject(i);
                if (same(old, record)) return false;
            } catch (Exception ignored) {
            }
        }
        queue.put(record);
        while (queue.length() > MAX_QUEUE) {
            queue.remove(0);
        }
        prefs.edit().putString(KEY_QUEUE, queue.toString()).apply();
        return true;
    }

    public static synchronized String takeQueue(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray queue = load(prefs);
        String value = queue.toString();
        prefs.edit().putString(KEY_QUEUE, "[]").apply();
        return value;
    }

    public static synchronized void setDiagnostic(Context context, JSONObject diagnostic) {
        if (diagnostic == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DIAG, diagnostic.toString())
                .apply();
    }

    public static synchronized String getDiagnostic(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DIAG, "");
    }

    private static JSONArray load(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_QUEUE, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static boolean same(JSONObject a, JSONObject b) {
        try {
            if (!a.optString("date", "").equals(b.optString("date", ""))) return false;
            if (Math.abs(a.optDouble("amount", -1) - b.optDouble("amount", -1)) > 0.004) return false;
            if (!a.optString("note", "").equals(b.optString("note", ""))) return false;
            if (!a.optString("platform", "").equals(b.optString("platform", ""))) return false;
            long delta = Math.abs(a.optLong("ts", 0) - b.optLong("ts", 0));
            return delta < 120000;
        } catch (Exception e) {
            return false;
        }
    }
}
