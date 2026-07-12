package com.example.myapplication;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.util.Size;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThumbnailCacheManager {

    private static final String TAG = "ThumbnailCache";
    private static final int THUMBNAIL_SIZE_PX = 200;

    public interface Callback {
        void onLoaded(String path, Bitmap bitmap);
    }

    private static ThumbnailCacheManager instance;

    private final LruCache<String, Bitmap> memoryCache;
    private final ExecutorService executorService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, List<Callback>> inFlightCallbacks = new HashMap<>();

    private ThumbnailCacheManager() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 10;

        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return Math.max(1, bitmap.getByteCount() / 1024);
            }
        };

        // 线程数降低，减少抢资源
        executorService = Executors.newFixedThreadPool(2);
    }

    public static synchronized ThumbnailCacheManager getInstance() {
        if (instance == null) {
            instance = new ThumbnailCacheManager();
        }
        return instance;
    }

    public Bitmap getCached(String path) {
        return memoryCache.get(path);
    }

    public void loadAsync(Context context, String path, Callback callback) {
        Bitmap cached = memoryCache.get(path);
        if (cached != null) {
            if (callback != null) callback.onLoaded(path, cached);
            return;
        }

        // Use the application context so a queued task can't leak the calling Activity.
        Context applicationContext = context.getApplicationContext();
        Context appContext = applicationContext != null ? applicationContext : context;

        synchronized (inFlightCallbacks) {
            List<Callback> callbacks = inFlightCallbacks.get(path);
            if (callbacks != null) {
                if (callback != null) callbacks.add(callback);
                return;
            }
            List<Callback> newCallbacks = new ArrayList<>();
            if (callback != null) newCallbacks.add(callback);
            inFlightCallbacks.put(path, newCallbacks);
        }

        executorService.execute(() -> {
            Bitmap bitmap = createThumbnail(appContext, path);
            if (bitmap != null) {
                memoryCache.put(path, bitmap);
            }

            List<Callback> callbacks;
            synchronized (inFlightCallbacks) {
                callbacks = inFlightCallbacks.remove(path);
            }
            if (callbacks == null || callbacks.isEmpty()) {
                return;
            }

            mainHandler.post(() -> {
                for (Callback cb : callbacks) {
                    cb.onLoaded(path, bitmap);
                }
            });
        });
    }

    private Bitmap createThumbnail(Context context, String path) {
        try {
            Uri uri = Uri.parse(path);

            // Android 10+ 优先使用系统缩略图
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                return resolver.loadThumbnail(
                        uri,
                        new Size(THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX),
                        null
                );
            }

            // 旧版本兜底
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(context, uri);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    return retriever.getScaledFrameAtTime(
                            0,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            THUMBNAIL_SIZE_PX,
                            THUMBNAIL_SIZE_PX
                    );
                }
                Bitmap frame = retriever.getFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                );
                return scaleDown(frame);
            } finally {
                retriever.release();
            }

        } catch (Exception e) {
            // Missing/deleted media and revoked partial access are expected cache misses.
            Log.d(TAG, "Unable to create a video thumbnail");
            return null;
        }
    }

    private Bitmap scaleDown(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largestSide = Math.max(width, height);
        if (largestSide <= THUMBNAIL_SIZE_PX || width <= 0 || height <= 0) {
            return bitmap;
        }

        float scale = (float) THUMBNAIL_SIZE_PX / (float) largestSide;
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true
        );
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }
}
