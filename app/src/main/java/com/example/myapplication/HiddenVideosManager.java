package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class HiddenVideosManager {

    private static final String PREF_NAME = "hidden_videos";
    private static final String KEY_HIDDEN = "hidden_set";
    private static final Object PREF_LOCK = new Object();
    private final SharedPreferences prefs;

    public HiddenVideosManager(Context context) {
        Context appContext = context.getApplicationContext();
        Context safeContext = appContext != null ? appContext : context;
        prefs = safeContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void hide(String path) {
        if (path == null) {
            return;
        }
        synchronized (PREF_LOCK) {
            Set<String> hiddenPaths = readHiddenPaths();
            if (hiddenPaths.add(path)) {
                prefs.edit().putStringSet(KEY_HIDDEN, hiddenPaths).apply();
            }
        }
    }

    public boolean isHidden(String path) {
        if (path == null) {
            return false;
        }
        synchronized (PREF_LOCK) {
            Set<String> storedPaths = prefs.getStringSet(KEY_HIDDEN, null);
            return storedPaths != null && storedPaths.contains(path);
        }
    }

    public void unhide(String path) {
        if (path == null) {
            return;
        }
        synchronized (PREF_LOCK) {
            Set<String> hiddenPaths = readHiddenPaths();
            if (hiddenPaths.remove(path)) {
                prefs.edit().putStringSet(KEY_HIDDEN, hiddenPaths).apply();
            }
        }
    }

    private Set<String> readHiddenPaths() {
        Set<String> storedPaths = prefs.getStringSet(KEY_HIDDEN, null);
        return storedPaths == null
                ? new HashSet<>()
                : new HashSet<>(storedPaths);
    }
}
