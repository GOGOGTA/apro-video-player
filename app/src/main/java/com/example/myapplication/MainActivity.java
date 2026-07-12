package com.example.myapplication;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import android.app.PendingIntent;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerNotificationManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final long BACK_PRESS_INTERVAL = 2000L;
    private static final int MAX_PLAY_ATTACH_RETRIES = 6;
    private static final String PLAYBACK_CHANNEL_ID = "aplayer_playback";
    private static final int PLAYBACK_NOTIFICATION_ID = 1201;

    private final List<VideoItem> videoList = new ArrayList<>();
    private final List<String> playerPlaylistPaths = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor();

    private DrawerLayout drawerLayout;
    private ViewPager2 viewPager;
    private RecyclerView rvVideoGrid;
    private VideoPagerAdapter videoPagerAdapter;
    private VideoGridAdapter videoGridAdapter;
    private View emptyStateView;
    private View btnMenu;
    private View btnSettings;
    private TextView tvEmptyMessage;
    private TextView tvDrawerTitle;
    private TextView tvCopyright;
    private TextView btnVideoManage;
    private TextView tvVideoCount;
    private ExoPlayer player;
    private HiddenVideosManager hiddenVideosManager;
    private VideoOrderManager videoOrderManager;
    private PlaybackSettingsManager playbackSettingsManager;
    private MediaSession mediaSession;
    private PlayerNotificationManager playerNotificationManager;
    private OppoFlowCloudBridge oppoFlowCloudBridge;
    private ItemTouchHelper itemTouchHelper;
    private long lastBackPressTime = 0L;
    private int loadGeneration = 0;
    private boolean destroyed = false;
    private long totalVideoBytes = 0L;
    private int currentPlayingIndex = -1;
    private boolean suppressNextPagePlay = false;
    private final Map<String, String> artistCache = new HashMap<>();
    private final Set<String> artistInFlight = new HashSet<>();
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();

    private static final class VideoLoadResult {
        final List<VideoItem> videos;
        final long totalBytes;

        VideoLoadResult(List<VideoItem> videos, long totalBytes) {
            this.videos = videos;
            this.totalBytes = totalBytes;
        }
    }

    private final Player.Listener mainPlayerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playerNotificationManager != null) {
                playerNotificationManager.invalidate();
            }
            syncFlowCloudFromCurrentState();
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            if (player == null || viewPager == null || videoList.isEmpty()) {
                return;
            }
            int index = player.getCurrentMediaItemIndex();
            if (index < 0 || index >= videoList.size()) {
                return;
            }
            currentPlayingIndex = index;
            if (videoGridAdapter != null) {
                videoGridAdapter.setSelectedPosition(index);
            }
            if (viewPager.getCurrentItem() != index) {
                suppressNextPagePlay = true;
                viewPager.setCurrentItem(index, true);
            }
            if (playerNotificationManager != null) {
                playerNotificationManager.invalidate();
            }
            syncFlowCloudFromCurrentState();
        }

    };

    private final ActivityResultLauncher<Intent> manageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            loadVideos();
                        }
                    });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    initMediaSessionAndNotification();
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.createLocalizedContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LanguageManager.applySavedLanguage(this);
        super.onCreate(savedInstanceState);
        enableLockscreenDisplay();
        setContentView(R.layout.activity_main);
        requestNotificationPermissionIfNeeded();

        hiddenVideosManager = new HiddenVideosManager(this);
        videoOrderManager = new VideoOrderManager(this);
        playbackSettingsManager = new PlaybackSettingsManager(this);
        initViews();
        initBackPressedHandler();
        initPlayer();
        checkPermissionsAndLoadVideos();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        viewPager = findViewById(R.id.viewPager);
        emptyStateView = findViewById(R.id.emptyStateView);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        rvVideoGrid = findViewById(R.id.rvVideoGrid);
        tvVideoCount = findViewById(R.id.tvVideoCount);
        btnMenu = findViewById(R.id.btnMenu);
        btnSettings = findViewById(R.id.btnSettings);
        tvDrawerTitle = findViewById(R.id.tvDrawerTitle);
        tvCopyright = findViewById(R.id.tvCopyright);
        btnVideoManage = findViewById(R.id.btnVideoManage);

        videoPagerAdapter = new VideoPagerAdapter(this, videoList);
        viewPager.setAdapter(videoPagerAdapter);
        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager.setOffscreenPageLimit(1);

        videoGridAdapter = new VideoGridAdapter(videoList, position -> {
            viewPager.setCurrentItem(position, false);
            drawerLayout.closeDrawer(GravityCompat.START);
            viewPager.post(() -> playVideoAt(position));
        });

        rvVideoGrid.setLayoutManager(new GridLayoutManager(this, 3));
        rvVideoGrid.setHasFixedSize(true);
        rvVideoGrid.setItemViewCacheSize(12);
        rvVideoGrid.setAdapter(videoGridAdapter);

        videoGridAdapter.setOnDragStartListener(holder -> {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);
            itemTouchHelper.startDrag(holder);
        });

        ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv,
                                        @NonNull RecyclerView.ViewHolder vh) {
                return makeMovementFlags(
                        ItemTouchHelper.UP | ItemTouchHelper.DOWN
                                | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT,
                        0
                );
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getAdapterPosition();
                int toPos = to.getAdapterPosition();
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false;
                }

                videoGridAdapter.onItemMove(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
                super.onSelectedChanged(vh, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    vh.itemView.setElevation(16f);
                    vh.itemView.setAlpha(0.90f);
                    vh.itemView.animate()
                            .scaleX(0.85f)
                            .scaleY(0.85f)
                            .setDuration(80)
                            .start();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                vh.itemView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220)
                        .start();
                vh.itemView.setElevation(0f);
                vh.itemView.setAlpha(1f);
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

                videoOrderManager.saveOrder(videoList);
                videoPagerAdapter.updateData(videoList);
                int syncedPosition = syncPlayerPlaylistWithVideoOrder();
                videoGridAdapter.setSelectedPosition(
                        syncedPosition >= 0 ? syncedPosition : viewPager.getCurrentItem()
                );
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(rvVideoGrid);

        setMenuButtonVisible(false);
        btnMenu.setOnClickListener(v -> openVideoListDrawer());
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.btnVideoManage).setOnClickListener(v -> {
            Intent intent = new Intent(this, VideoManageActivity.class);
            manageLauncher.launch(intent);
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                videoGridAdapter.setSelectedPosition(position);
                setMenuButtonVisible(false);
                if (suppressNextPagePlay) {
                    suppressNextPagePlay = false;
                    // Player already advanced to this item internally (auto-play next /
                    // notification skip); just move its video surface onto the new page.
                    attachPlayerToPage(position, 0);
                    return;
                }
                playVideoAt(position);
            }
        });

        refreshLocalizedTexts();
    }

    public void setPagerSwipeEnabled(boolean enabled) {
        if (viewPager != null) {
            viewPager.setUserInputEnabled(enabled);
        }
    }

    public void setMenuButtonVisible(boolean visible) {
        if (btnMenu == null) {
            return;
        }
        btnMenu.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public boolean isCurrentVideoPosition(int position) {
        return viewPager != null && position == viewPager.getCurrentItem();
    }

    public void openVideoListDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    public void playNextFromWidget() {
        if (videoList.isEmpty() || viewPager == null) {
            return;
        }
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
            player.play();
            return;
        }
        int nextIndex = 0;
        viewPager.setCurrentItem(nextIndex, true);
    }

    public void playPreviousFromWidget() {
        if (videoList.isEmpty() || viewPager == null) {
            return;
        }
        if (player != null && player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem();
            player.play();
            return;
        }
        int prevIndex = videoList.size() - 1;
        viewPager.setCurrentItem(prevIndex, true);
    }

    private void initBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }

                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < BACK_PRESS_INTERVAL) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }

                lastBackPressTime = now;
                Toast.makeText(
                        MainActivity.this,
                        LanguageManager.getLocalizedString(MainActivity.this, R.string.press_again_to_exit),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void enableLockscreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void initMediaSessionAndNotification() {
        if (player == null) {
            return;
        }
        if (mediaSession == null) {
            mediaSession = new MediaSession.Builder(this, player).build();
        }
        if (!hasNotificationPermission()) {
            return;
        }
        if (playerNotificationManager == null) {
            PlayerNotificationManager.Builder builder = new PlayerNotificationManager.Builder(
                    this,
                    PLAYBACK_NOTIFICATION_ID,
                    PLAYBACK_CHANNEL_ID
            )
                    .setChannelNameResourceId(R.string.playback_channel_name)
                    .setChannelDescriptionResourceId(R.string.playback_channel_desc)
                    .setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
                        @Override
                        public CharSequence getCurrentContentTitle(Player player) {
                            VideoItem item = currentVideoItem();
                            return item != null ? displayTitle(item.getName()) : getString(R.string.app_name);
                        }

                        @Override
                        public PendingIntent createCurrentContentIntent(Player player) {
                            return null;
                        }

                        @Override
                        public CharSequence getCurrentContentText(Player player) {
                            VideoItem item = currentVideoItem();
                            if (item == null) {
                                return getString(R.string.widget_unknown_artist);
                            }
                            return resolveArtist(item.getPath());
                        }

                        @Override
                        public Bitmap getCurrentLargeIcon(
                                Player player,
                                PlayerNotificationManager.BitmapCallback callback
                        ) {
                            VideoItem item = currentVideoItem();
                            if (item == null) {
                                return null;
                            }
                            Bitmap cached = ThumbnailCacheManager.getInstance().getCached(item.getPath());
                            if (cached != null) {
                                return cached;
                            }
                            ThumbnailCacheManager.getInstance().loadAsync(
                                    MainActivity.this,
                                    item.getPath(),
                                    (path, bitmap) -> {
                                        VideoItem current = currentVideoItem();
                                        if (bitmap != null && current != null && path.equals(current.getPath())) {
                                            callback.onBitmap(bitmap);
                                        }
                                    }
                            );
                            return null;
                        }
                    });

            playerNotificationManager = builder.build();
            playerNotificationManager.setUsePlayPauseActions(true);
            playerNotificationManager.setUseNextAction(true);
            playerNotificationManager.setUseNextActionInCompactView(true);
            playerNotificationManager.setUsePreviousAction(true);
            playerNotificationManager.setUsePreviousActionInCompactView(true);
            playerNotificationManager.setUseFastForwardAction(false);
            playerNotificationManager.setUseRewindAction(false);
            playerNotificationManager.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            attachMediaSessionTokenCompat(playerNotificationManager, mediaSession);
        }
        playerNotificationManager.setPlayer(player);
        playerNotificationManager.invalidate();
    }

    private void attachMediaSessionTokenCompat(
            @NonNull PlayerNotificationManager notificationManager,
            @NonNull MediaSession session
    ) {
        try {
            Method getCompatTokenMethod = MediaSession.class.getMethod("getSessionCompatToken");
            Object compatToken = getCompatTokenMethod.invoke(session);
            if (compatToken == null) {
                return;
            }
            Method setTokenMethod = PlayerNotificationManager.class.getMethod(
                    "setMediaSessionToken",
                    compatToken.getClass()
            );
            setTokenMethod.invoke(notificationManager, compatToken);
        } catch (Throwable ignored) {
        }
    }

    private void releaseMediaSessionAndNotification() {
        if (playerNotificationManager != null) {
            playerNotificationManager.setPlayer(null);
            playerNotificationManager = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    @Nullable
    private VideoItem currentVideoItem() {
        if (videoList.isEmpty() || player == null) {
            return null;
        }
        int index = player.getCurrentMediaItemIndex();
        if (index < 0 || index >= videoList.size()) {
            index = currentPlayingIndex;
        }
        if (index < 0 || index >= videoList.size()) {
            return null;
        }
        return videoList.get(index);
    }

    private String displayTitle(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return getString(R.string.widget_unknown_track);
        }
        int dot = rawName.lastIndexOf('.');
        String title = dot > 0 ? rawName.substring(0, dot) : rawName;
        title = title.replace('_', ' ').replace('-', ' ');
        title = title.replaceAll("(?i)^(vid|video|rec|record|audio|song)\\s*", "");
        title = title.replaceAll("^\\d{4}\\s*\\d{2}\\s*\\d{2}\\s*", "");
        title = title.replaceAll("^\\d{2}\\s*\\d{2}\\s*\\d{2}\\s*", "");
        title = title.replaceAll("\\s{2,}", " ").trim();
        return title.isEmpty() ? getString(R.string.widget_unknown_track) : title;
    }

    private String resolveArtist(String path) {
        if (path == null || path.trim().isEmpty()) {
            return getString(R.string.widget_unknown_artist);
        }
        String cachedArtist = artistCache.get(path);
        if (cachedArtist != null) {
            return cachedArtist;
        }
        // Metadata extraction is blocking I/O, so resolve it off the main thread and
        // refresh the notification / Fluid Cloud once the real artist is available.
        requestArtistAsync(path);
        return getString(R.string.widget_unknown_artist);
    }

    private void requestArtistAsync(String path) {
        if (destroyed || artistInFlight.contains(path)) {
            return;
        }
        artistInFlight.add(path);
        metadataExecutor.execute(() -> {
            String resolved = extractArtist(path);
            mainHandler.post(() -> {
                artistInFlight.remove(path);
                if (destroyed) {
                    return;
                }
                artistCache.put(path, resolved);
                VideoItem current = currentVideoItem();
                if (current != null && path.equals(current.getPath())) {
                    if (playerNotificationManager != null) {
                        playerNotificationManager.invalidate();
                    }
                    syncFlowCloudFromCurrentState();
                }
            });
        });
    }

    private String extractArtist(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(getApplicationContext(), Uri.parse(path));
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (artist == null || artist.trim().isEmpty()) {
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            }
            if (artist == null || artist.trim().isEmpty()) {
                return getString(R.string.widget_unknown_artist);
            }
            return artist.trim();
        } catch (Exception ignored) {
            return getString(R.string.widget_unknown_artist);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.setRepeatMode(getRepeatMode());
        player.setPlaybackParameters(new PlaybackParameters(1.0f));
        player.addListener(mainPlayerListener);
        oppoFlowCloudBridge = new OppoFlowCloudBridge(this);
        initMediaSessionAndNotification();
    }

    private int getRepeatMode() {
        return (playbackSettingsManager != null && playbackSettingsManager.isAutoPlayNext())
                ? ExoPlayer.REPEAT_MODE_ALL
                : ExoPlayer.REPEAT_MODE_ONE;
    }

    private boolean isPlayerPlaylistCurrent() {
        if (player == null
                || player.getMediaItemCount() != videoList.size()
                || playerPlaylistPaths.size() != videoList.size()) {
            return false;
        }

        for (int i = 0; i < videoList.size(); i++) {
            String playerPath = playerPlaylistPaths.get(i);
            String videoPath = videoList.get(i).getPath();
            if (videoPath == null ? playerPath != null : !videoPath.equals(playerPath)) {
                return false;
            }
        }
        return true;
    }

    private boolean ensurePlayerPlaylist(int startIndex) {
        if (player == null || videoList.isEmpty()) {
            return false;
        }
        if (isPlayerPlaylistCurrent()) {
            return false;
        }

        replacePlayerPlaylist(startIndex, 0L);
        return true;
    }

    private void replacePlayerPlaylist(int startIndex, long startPositionMs) {
        List<MediaItem> items = new ArrayList<>(videoList.size());
        playerPlaylistPaths.clear();
        for (VideoItem item : videoList) {
            String path = item.getPath();
            items.add(MediaItem.fromUri(Uri.parse(path)));
            playerPlaylistPaths.add(path);
        }

        int safeIndex = Math.max(0, Math.min(startIndex, items.size() - 1));
        player.setMediaItems(items, safeIndex, Math.max(0L, startPositionMs));
        player.prepare();
    }

    private int syncPlayerPlaylistWithVideoOrder() {
        if (player == null || videoList.isEmpty() || isPlayerPlaylistCurrent()) {
            return -1;
        }

        String currentPath = currentPlayerPath();
        int targetIndex = findVideoIndexByPath(currentPath);
        long startPositionMs = 0L;
        if (targetIndex >= 0) {
            startPositionMs = Math.max(0L, player.getCurrentPosition());
        } else if (viewPager != null) {
            targetIndex = Math.max(0, Math.min(viewPager.getCurrentItem(), videoList.size() - 1));
        } else {
            targetIndex = 0;
        }

        boolean shouldPlay = player.getPlayWhenReady();
        replacePlayerPlaylist(targetIndex, startPositionMs);
        currentPlayingIndex = targetIndex;
        if (viewPager != null && viewPager.getCurrentItem() != targetIndex) {
            suppressNextPagePlay = true;
            viewPager.setCurrentItem(targetIndex, false);
        }
        if (shouldPlay) {
            player.play();
        } else {
            player.pause();
        }
        if (playerNotificationManager != null) {
            playerNotificationManager.invalidate();
        }
        syncFlowCloudFromCurrentState();
        return targetIndex;
    }

    @Nullable
    private String currentPlayerPath() {
        if (player == null) {
            return null;
        }
        int index = player.getCurrentMediaItemIndex();
        if (index >= 0 && index < playerPlaylistPaths.size()) {
            return playerPlaylistPaths.get(index);
        }
        VideoItem item = currentVideoItem();
        return item != null ? item.getPath() : null;
    }

    private int findVideoIndexByPath(@Nullable String path) {
        if (path == null) {
            return -1;
        }
        for (int i = 0; i < videoList.size(); i++) {
            if (path.equals(videoList.get(i).getPath())) {
                return i;
            }
        }
        return -1;
    }

    private void clearPlayerPlaylist() {
        currentPlayingIndex = -1;
        playerPlaylistPaths.clear();
        if (videoPagerAdapter != null) {
            videoPagerAdapter.detachAllPlayers();
        }
        if (player != null) {
            player.pause();
            player.clearMediaItems();
        }
        if (playerNotificationManager != null) {
            playerNotificationManager.invalidate();
        }
        syncFlowCloudFromCurrentState();
    }

    private void checkPermissionsAndLoadVideos() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{permission},
                    PERMISSION_REQUEST_CODE
            );
        } else {
            loadVideos();
        }
    }

    private void loadVideos() {
        final int generation = ++loadGeneration;
        loadExecutor.execute(() -> {
            VideoLoadResult result = queryVisibleVideos();
            if (destroyed || generation != loadGeneration) {
                return;
            }

            mainHandler.post(() -> applyLoadedVideos(result, generation));
        });
    }

    private VideoLoadResult queryVisibleVideos() {
        List<VideoItem> loadedVideos = new ArrayList<>();
        long totalBytes = 0L;
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
                int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIndex);
                    String name = cursor.getString(nameIndex);
                    long size = cursor.getLong(sizeIndex);
                    Uri videoUri = ContentUris.withAppendedId(collection, id);
                    if (!hiddenVideosManager.isHidden(videoUri.toString())) {
                        loadedVideos.add(new VideoItem(videoUri.toString(), name, size));
                        totalBytes += Math.max(size, 0L);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        applySavedOrder(loadedVideos);
        return new VideoLoadResult(loadedVideos, totalBytes);
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

    private void applyLoadedVideos(VideoLoadResult result, int generation) {
        if (destroyed || generation != loadGeneration) {
            return;
        }

        videoList.clear();
        videoList.addAll(result.videos);
        totalVideoBytes = result.totalBytes;

        tvVideoCount.setText(LanguageManager.getLocalizedString(
                this,
                R.string.video_count_format,
                videoList.size(),
                FileSizeFormatter.formatDetailedSize(this, totalVideoBytes)
        ));
        videoPagerAdapter.updateData(videoList);
        videoGridAdapter.updateData();

        if (videoList.isEmpty()) {
            emptyStateView.setVisibility(View.VISIBLE);
            setMenuButtonVisible(false);
            clearPlayerPlaylist();
            viewPager.setCurrentItem(0, false);
            return;
        }

        emptyStateView.setVisibility(View.GONE);
        viewPager.post(() -> {
            if (destroyed || generation != loadGeneration || videoList.isEmpty()) {
                return;
            }
            viewPager.setCurrentItem(0, false);
            videoGridAdapter.setSelectedPosition(0);
            playVideoAt(0);
        });
    }

    private void playVideoAt(int position) {
        playVideoAt(position, 0);
    }

    private void playVideoAt(int position, int retryCount) {
        if (player == null || position < 0 || position >= videoList.size()) {
            return;
        }
        if (viewPager == null || viewPager.getChildCount() == 0) {
            return;
        }

        RecyclerView recyclerView = (RecyclerView) viewPager.getChildAt(0);
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);

        if (!(holder instanceof VideoPagerAdapter.ViewHolder)) {
            if (retryCount < MAX_PLAY_ATTACH_RETRIES) {
                recyclerView.postOnAnimation(() -> playVideoAt(position, retryCount + 1));
            }
            return;
        }

        VideoPagerAdapter.ViewHolder videoHolder = (VideoPagerAdapter.ViewHolder) holder;
        videoPagerAdapter.detachPlayersExcept(videoHolder);
        videoHolder.attachPlayer(player);

        player.setRepeatMode(getRepeatMode());
        boolean playlistRebuilt = ensurePlayerPlaylist(position);
        if (!playlistRebuilt) {
            if (player.getCurrentMediaItemIndex() != position) {
                player.seekToDefaultPosition(position);
            } else if (player.getPlaybackState() == Player.STATE_IDLE) {
                player.prepare();
            }
        }
        currentPlayingIndex = position;
        player.play();
        if (playerNotificationManager != null) {
            playerNotificationManager.invalidate();
        }
        syncFlowCloudFromCurrentState();
    }

    /**
     * Moves the shared player's video surface onto the page at {@code position} without rebuilding
     * the playlist. Used when the player has already advanced internally (auto-play next or a
     * notification skip), so playback continues seamlessly while the visible page follows along.
     */
    private void attachPlayerToPage(int position, int retryCount) {
        if (player == null || position < 0 || position >= videoList.size()) {
            return;
        }
        if (viewPager == null || viewPager.getChildCount() == 0) {
            return;
        }

        RecyclerView recyclerView = (RecyclerView) viewPager.getChildAt(0);
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);

        if (!(holder instanceof VideoPagerAdapter.ViewHolder)) {
            if (retryCount < MAX_PLAY_ATTACH_RETRIES) {
                recyclerView.postOnAnimation(() -> attachPlayerToPage(position, retryCount + 1));
            }
            return;
        }

        VideoPagerAdapter.ViewHolder videoHolder = (VideoPagerAdapter.ViewHolder) holder;
        videoPagerAdapter.detachPlayersExcept(videoHolder);
        videoHolder.attachPlayer(player);
        currentPlayingIndex = position;
        player.play();
        if (playerNotificationManager != null) {
            playerNotificationManager.invalidate();
        }
        syncFlowCloudFromCurrentState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
        syncFlowCloudFromCurrentState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LanguageManager.applySavedLanguage(this);
        refreshLocalizedTexts();
        initMediaSessionAndNotification();
        if (player == null || videoList.isEmpty()) {
            return;
        }
        // Re-apply repeat mode so a change to the "auto-play next" setting takes effect on return.
        player.setRepeatMode(getRepeatMode());
        if (player.getMediaItemCount() == 0) {
            // Media not attached yet (e.g. returning before the initial load finished) -> set up.
            viewPager.post(() -> playVideoAt(viewPager.getCurrentItem()));
        } else {
            // Already prepared -> resume from the current position instead of restarting.
            player.play();
        }
    }

    private void refreshLocalizedTexts() {
        if (tvEmptyMessage != null) {
            tvEmptyMessage.setText(LanguageManager.getLocalizedString(this, R.string.empty_video_message));
        }
        if (tvDrawerTitle != null) {
            tvDrawerTitle.setText(LanguageManager.getLocalizedString(this, R.string.video_list_title));
        }
        if (btnVideoManage != null) {
            btnVideoManage.setText(LanguageManager.getLocalizedString(this, R.string.video_manage));
        }
        if (tvCopyright != null) {
            tvCopyright.setText(LanguageManager.getLocalizedString(this, R.string.copyright_text));
        }
        if (tvVideoCount != null) {
            tvVideoCount.setText(LanguageManager.getLocalizedString(
                    this,
                    R.string.video_count_format,
                    videoList.size(),
                    FileSizeFormatter.formatDetailedSize(this, totalVideoBytes)
            ));
        }
        if (btnMenu != null) {
            btnMenu.setContentDescription(LanguageManager.getLocalizedString(this, R.string.open_video_list));
        }
        if (btnSettings != null) {
            btnSettings.setContentDescription(LanguageManager.getLocalizedString(this, R.string.settings_title));
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (oppoFlowCloudBridge != null) {
            oppoFlowCloudBridge.clear();
        }
        if (player != null) {
            player.removeListener(mainPlayerListener);
        }
        releaseMediaSessionAndNotification();
        if (videoPagerAdapter != null) {
            videoPagerAdapter.detachAllPlayers();
        }
        if (player != null) {
            player.release();
            player = null;
        }
        loadExecutor.shutdownNow();
        metadataExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadVideos();
            } else {
                Toast.makeText(this, LanguageManager.getLocalizedString(this, R.string.storage_permission_required), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void syncFlowCloudFromCurrentState() {
        if (oppoFlowCloudBridge == null) {
            return;
        }
        VideoItem item = currentVideoItem();
        if (item == null || player == null) {
            oppoFlowCloudBridge.clear();
            return;
        }
        long positionMs = Math.max(0L, player.getCurrentPosition());
        long durationMs = Math.max(0L, player.getDuration());
        boolean isPlaying = player.isPlaying();
        oppoFlowCloudBridge.sync(
                item,
                displayTitle(item.getName()),
                resolveArtist(item.getPath()),
                positionMs,
                durationMs,
                isPlaying
        );
    }
}
