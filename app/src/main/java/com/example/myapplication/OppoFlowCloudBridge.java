package com.example.myapplication;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.oplus.pantanal.seedling.bean.SeedlingHostEnum;
import com.oplus.pantanal.seedling.intelligent.IntelligentData;
import com.oplus.pantanal.seedling.update.SeedlingCardOptions;
import com.oplus.pantanal.seedling.util.SeedlingTool;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Pushes playback state to OPPO Seedling (Fluid Cloud) with minimal coupling to UI logic.
 */
public final class OppoFlowCloudBridge {

    private static final String TAG = "OppoFlowCloudBridge";
    private static final int MUSIC_EVENT_CODE = 10601;
    private static final String MUSIC_EVENT_NAME = "music_playback";
    private static final int STATE_SHOW = 1;
    private static final int STATE_HIDE = 0;

    private final Context appContext;
    private final String serviceInstanceId;
    private boolean supportChecked = false;
    private boolean supportFluidCloud = false;

    public OppoFlowCloudBridge(Context context) {
        appContext = context.getApplicationContext();
        String instanceId;
        try {
            instanceId = SeedlingTool.INSTANCE.genServiceInstanceId();
        } catch (Throwable ignored) {
            instanceId = "music-instance";
        }
        serviceInstanceId = instanceId;
    }

    public void sync(
            @Nullable VideoItem item,
            String title,
            String artist,
            long positionMs,
            long durationMs,
            boolean isPlaying
    ) {
        if (item == null) {
            updateState(STATE_HIDE, null, null, 0L, 0L, false, null);
            return;
        }
        updateState(
                STATE_SHOW,
                title,
                artist,
                Math.max(0L, positionMs),
                Math.max(0L, durationMs),
                isPlaying,
                item.getPath()
        );
    }

    public void clear() {
        updateState(STATE_HIDE, null, null, 0L, 0L, false, null);
    }

    private void updateState(
            int state,
            @Nullable String title,
            @Nullable String artist,
            long positionMs,
            long durationMs,
            boolean isPlaying,
            @Nullable String mediaPath
    ) {
        if (!ensureSupport()) {
            return;
        }
        try {
            JSONObject dataJson = new JSONObject();
            dataJson.put("state", state);
            dataJson.put("position", positionMs);
            dataJson.put("duration", durationMs);
            dataJson.put("isPlaying", isPlaying);

            JSONObject businessJson = new JSONObject();
            if (title != null) {
                businessJson.put("title", title);
            }
            if (artist != null) {
                businessJson.put("artist", artist);
            }
            businessJson.put("position", positionMs);
            businessJson.put("duration", durationMs);
            businessJson.put("isPlaying", isPlaying);
            if (mediaPath != null) {
                businessJson.put("mediaPath", mediaPath);
            }

            IntelligentData intelligentData = new IntelligentData(
                    System.currentTimeMillis(),
                    MUSIC_EVENT_CODE,
                    MUSIC_EVENT_NAME,
                    dataJson,
                    businessJson,
                    buildCardOptions(),
                    serviceInstanceId
            );
            SeedlingTool.INSTANCE.updateIntelligentData(appContext, intelligentData);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to update fluid cloud", t);
        }
    }

    private SeedlingCardOptions buildCardOptions() {
        SeedlingCardOptions options = new SeedlingCardOptions();
        options.setMilestone(true);
        options.setGrade(SeedlingCardOptions.GRADE_3);
        options.setRequestShowPanel(Boolean.TRUE);
        options.setRequestHideStatusBar(false);
        options.setNotificationIdList(java.util.Collections.singletonList(1201));

        Map<SeedlingHostEnum, Boolean> showHostMap = new HashMap<>();
        showHostMap.put(SeedlingHostEnum.StatusBar, true);
        showHostMap.put(SeedlingHostEnum.Notification, true);
        options.setShowHostMap(showHostMap);

        Map<SeedlingHostEnum, Boolean> lockShowHostMap = new HashMap<>();
        lockShowHostMap.put(SeedlingHostEnum.StatusBar, true);
        lockShowHostMap.put(SeedlingHostEnum.Notification, true);
        options.setLockScreenShowHostMap(lockShowHostMap);
        return options;
    }

    private boolean ensureSupport() {
        if (supportChecked) {
            return supportFluidCloud;
        }
        supportChecked = true;
        try {
            supportFluidCloud = SeedlingTool.isSupportSeedlingCard(appContext)
                    && SeedlingTool.isSupportFluidCloud(appContext);
        } catch (Throwable ignored) {
            supportFluidCloud = false;
        }
        return supportFluidCloud;
    }
}
