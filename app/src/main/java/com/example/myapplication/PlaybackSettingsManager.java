package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

public class PlaybackSettingsManager {

    private static final String PREFS_NAME = "playback_settings";
    private static final String KEY_AUTO_PLAY_NEXT = "auto_play_next";

    private final SharedPreferences prefs;

    public PlaybackSettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAutoPlayNext() {
        return prefs.getBoolean(KEY_AUTO_PLAY_NEXT, false);
    }

    public void setAutoPlayNext(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply();
    }
}
