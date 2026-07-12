package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class HiddenVideosManager {

    private static final String PREF_NAME = "hidden_videos";
    private static final String KEY_HIDDEN = "hidden_set";
    private final SharedPreferences prefs;
    private Set<String> hiddenCache;

    public HiddenVideosManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        hiddenCache = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<>()));
    }

    public void hide(String path) {
        if (hiddenCache.add(path)) {
            prefs.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hiddenCache)).apply();
        }
    }

    public boolean isHidden(String path) {
        return hiddenCache.contains(path);
    }

    public void unhide(String path) {
        if (hiddenCache.remove(path)) {
            prefs.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hiddenCache)).apply();
        }
    }
}
