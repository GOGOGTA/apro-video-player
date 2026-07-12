package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

public class PlaybackSettingsManager {

    private static final String PREFS_NAME = "playback_settings";
    private static final String KEY_AUTO_PLAY_NEXT = "auto_play_next";
    private static final String KEY_RANDOM_PLAY_ON_START = "random_play_on_start";

    private final SharedPreferences prefs;

    public PlaybackSettingsManager(Context context) {
        Context appContext = context.getApplicationContext();
        Context safeContext = appContext != null ? appContext : context;
        prefs = safeContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAutoPlayNext() {
        return prefs.getBoolean(KEY_AUTO_PLAY_NEXT, false);
    }

    public void setAutoPlayNext(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply();
    }

    public boolean isRandomPlayOnStart() {
        return prefs.getBoolean(KEY_RANDOM_PLAY_ON_START, false);
    }

    public void setRandomPlayOnStart(boolean enabled) {
        prefs.edit().putBoolean(KEY_RANDOM_PLAY_ON_START, enabled).apply();
    }
}
