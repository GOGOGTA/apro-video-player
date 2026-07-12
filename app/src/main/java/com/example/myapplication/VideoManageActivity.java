package com.example.myapplication;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoManageActivity extends AppCompatActivity {

    private static final String TAG = "VideoManageActivity";

    private final List<VideoItem> videoList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();

    private RecyclerView rvGrid;
    private MaterialToolbar toolbar;
    private TextView tvHint;
    private TextView tvCount;
    private VideoManageAdapter adapter;
    private HiddenVideosManager hiddenVideosManager;
    private VideoOrderManager videoOrderManager;
    private boolean changed = false;
    private int loadGeneration = 0;
    private boolean destroyed = false;
    private long totalVideoBytes = 0L;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.createLocalizedContext(newBase));
    }

    private static final class VideoLoadResult {
        final List<VideoItem> videos;
        final long totalBytes;
        final boolean successful;

        VideoLoadResult(List<VideoItem> videos, long totalBytes, boolean successful) {
            this.videos = videos;
            this.totalBytes = totalBytes;
            this.successful = successful;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LanguageManager.applySavedLanguage(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_manage);

        hiddenVideosManager = new HiddenVideosManager(this);
        videoOrderManager = new VideoOrderManager(this);

        rvGrid = findViewById(R.id.rvManageGrid);
        tvHint = findViewById(R.id.tvManageHint);
        tvCount = findViewById(R.id.tvManageCount);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new VideoManageAdapter(videoList, position -> {
            if (position < 0 || position >= videoList.size()) {
                return;
            }

            VideoItem removed = videoList.get(position);
            String path = removed.getPath();
            hiddenVideosManager.hide(path);
            videoList.remove(position);
            totalVideoBytes = Math.max(0L, totalVideoBytes - Math.max(0L, removed.getSizeBytes()));
            adapter.notifyItemRemoved(position);
            videoOrderManager.saveOrder(videoList);
            updateCount();
            changed = true;
        });

        rvGrid.setLayoutManager(new GridLayoutManager(this, 4));
        rvGrid.setHasFixedSize(true);
        rvGrid.setItemViewCacheSize(16);
        rvGrid.setAdapter(adapter);

        rvGrid.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    View child = rv.findChildViewUnder(e.getX(), e.getY());
                    if (child == null) {
                        adapter.clearDeleteMode();
                    }
                }
                return false;
            }
        });

        refreshLocalizedTexts();
        loadVideos();
    }

    private void loadVideos() {
        final int generation = ++loadGeneration;
        loadExecutor.execute(() -> {
            VideoLoadResult result = queryVisibleVideos();
            if (destroyed || generation != loadGeneration) {
                return;
            }

            mainHandler.post(() -> {
                if (destroyed || generation != loadGeneration) {
                    return;
                }
                if (!result.successful) {
                    return;
                }
                videoList.clear();
                videoList.addAll(result.videos);
                totalVideoBytes = result.totalBytes;
                adapter.notifyDataSetChanged();
                updateCount();
            });
        });
    }

    private VideoLoadResult queryVisibleVideos() {
        List<VideoItem> loadedVideos = new ArrayList<>();
        long totalBytes = 0L;
        boolean successful = false;
        ContentResolver contentResolver = getContentResolver();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE
        };

        try (Cursor cursor = contentResolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
        )) {
            if (cursor != null) {
                successful = true;
                int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIndex);
                    String name = cursor.getString(nameIndex);
                    long size = cursor.getLong(sizeIndex);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    if (!hiddenVideosManager.isHidden(uri.toString())) {
                        loadedVideos.add(new VideoItem(uri.toString(), name, size));
                        totalBytes += Math.max(size, 0L);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to query videos from MediaStore", e);
        }

        applySavedOrder(loadedVideos);
        return new VideoLoadResult(loadedVideos, totalBytes, successful);
    }

    private void applySavedOrder(List<VideoItem> loadedVideos) {
        List<String> savedOrder = videoOrderManager.loadOrder();
        if (savedOrder.isEmpty()) {
            return;
        }

        Map<String, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < savedOrder.size(); i++) {
            orderMap.put(savedOrder.get(i), i);
        }

        Collections.sort(loadedVideos, (a, b) -> {
            int ia = orderMap.containsKey(a.getPath()) ? orderMap.get(a.getPath()) : Integer.MAX_VALUE;
            int ib = orderMap.containsKey(b.getPath()) ? orderMap.get(b.getPath()) : Integer.MAX_VALUE;
            return Integer.compare(ia, ib);
        });
    }

    private void updateCount() {
        tvCount.setText(LanguageManager.getLocalizedString(
                this,
                R.string.video_count_format,
                videoList.size(),
                FileSizeFormatter.formatDetailedSize(this, totalVideoBytes)
        ));
    }

    @Override
    protected void onResume() {
        super.onResume();
        LanguageManager.applySavedLanguage(this);
        refreshLocalizedTexts();
    }

    private void refreshLocalizedTexts() {
        if (toolbar != null) {
            toolbar.setTitle(LanguageManager.getLocalizedString(this, R.string.manage_title));
        }
        if (tvHint != null) {
            tvHint.setText(LanguageManager.getLocalizedString(this, R.string.manage_hint));
        }
        updateCount();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        loadExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void finish() {
        if (changed) {
            setResult(RESULT_OK);
        }
        super.finish();
    }
}
