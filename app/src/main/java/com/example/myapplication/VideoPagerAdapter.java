package com.example.myapplication;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@UnstableApi
public class VideoPagerAdapter extends RecyclerView.Adapter<VideoPagerAdapter.ViewHolder> {

    private final MainActivity activity;
    private final List<VideoItem> videoList = new ArrayList<>();
    private final List<ViewHolder> attachedHolders = new ArrayList<>();

    public VideoPagerAdapter(MainActivity activity, List<VideoItem> videoList) {
        this.activity = activity;
        this.videoList.addAll(videoList);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new ViewHolder(view, activity);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (!attachedHolders.contains(holder)) {
            attachedHolders.add(holder);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        holder.detachPlayer();
        attachedHolders.remove(holder);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.detachPlayer();
        attachedHolders.remove(holder);
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public void updateData(List<VideoItem> newList) {
        videoList.clear();
        videoList.addAll(newList);
        notifyDataSetChanged();
    }

    public void detachAllPlayers() {
        List<ViewHolder> snapshot = new ArrayList<>(attachedHolders);
        for (ViewHolder holder : snapshot) {
            holder.detachPlayer();
        }
    }

    public void detachPlayersExcept(@NonNull ViewHolder holderToKeep) {
        List<ViewHolder> snapshot = new ArrayList<>(attachedHolders);
        for (ViewHolder holder : snapshot) {
            if (holder != holderToKeep) {
                holder.detachPlayer();
            }
        }
    }

    @UnstableApi
    public static class ViewHolder extends RecyclerView.ViewHolder {

        private static final long UI_UPDATE_INTERVAL_MS = 100L;
        private static final long PREVIEW_DISPATCH_INTERVAL_MS = 16L;
        private static final long PREVIEW_MIN_SEEK_INTERVAL_MS = 33L;
        private static final long SEEK_INFO_HIDE_DELAY_MS = 700L;
        private static final long SEEK_FEEDBACK_HIDE_DELAY_MS = 450L;
        private static final long CONTROLS_HIDE_DELAY_MS = 2500L;
        private static final long PREVIEW_DELTA_THRESHOLD_MS = 120L;
        private static final long FRAME_PREVIEW_DELTA_THRESHOLD_MS = 33L;
        private static final float CONTROLS_VISIBLE_ALPHA = 1f;
        private static final float CONTROLS_HIDDEN_ALPHA = 0f;

        private final MainActivity activity;
        private final PlayerView playerView;
        private final PrecisionSeekBarView precisionSeekBar;
        private final TextView tvSeekTimestamp;
        private final TextView tvSeekFeedback;
        private final float drawerSwipeThresholdPx;

        private ExoPlayer currentPlayer;
        private final GestureDetector gestureDetector;
        private final Handler progressHandler = new Handler(Looper.getMainLooper());
        private final Handler overlayHandler = new Handler(Looper.getMainLooper());
        private float touchDownX;
        private float touchDownY;
        private boolean drawerSwipeTriggered;

        private long lastPreviewSeekRealtimeMs;
        private long lastPreviewPositionMs = Long.MIN_VALUE;
        private long pendingPreviewPositionMs = Long.MIN_VALUE;
        private boolean previewDispatchScheduled;
        private boolean restorePlaybackAfterScrub;
        private boolean seekInfoHideScheduled;

        private final Runnable progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (currentPlayer == null) {
                    return;
                }
                syncProgressUi();
                progressHandler.postDelayed(this, UI_UPDATE_INTERVAL_MS);
            }
        };

        private final Runnable hideSeekInfoRunnable = new Runnable() {
            @Override
            public void run() {
                seekInfoHideScheduled = false;
                if (!precisionSeekBar.isScrubbing()) {
                    tvSeekTimestamp.setVisibility(View.GONE);
                }
            }
        };

        private final Runnable hideSeekFeedbackRunnable = new Runnable() {
            @Override
            public void run() {
                tvSeekFeedback.setVisibility(View.GONE);
            }
        };

        private final Runnable hideControlsRunnable = new Runnable() {
            @Override
            public void run() {
                if (precisionSeekBar.isScrubbing()) {
                    scheduleControlsHide(380L);
                    return;
                }
                hidePlaybackControls();
            }
        };

        private final Runnable previewSeekRunnable = new Runnable() {
            @Override
            public void run() {
                previewDispatchScheduled = false;
                dispatchLatestPreviewSeek(false);
            }
        };

        private final Player.Listener playerListener = new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                syncProgressUi();
            }

            @Override
            public void onIsLoadingChanged(boolean isLoading) {
                syncBufferingState();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                precisionSeekBar.setFrameStepEnabled(!isPlaying);
                syncProgressUi();
            }

            @Override
            public void onPositionDiscontinuity(
                    @NonNull Player.PositionInfo oldPosition,
                    @NonNull Player.PositionInfo newPosition,
                    int reason
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK && precisionSeekBar.isScrubbing()) {
                    dispatchLatestPreviewSeek(true);
                }
            }
        };

        public ViewHolder(@NonNull View itemView, MainActivity activity) {
            super(itemView);
            this.activity = activity;

            playerView = itemView.findViewById(R.id.playerView);
            precisionSeekBar = itemView.findViewById(R.id.precisionSeekBar);
            tvSeekTimestamp = itemView.findViewById(R.id.tvSeekTimestamp);
            tvSeekFeedback = itemView.findViewById(R.id.tvSeekFeedback);
            float density = itemView.getResources().getDisplayMetrics().density;
            drawerSwipeThresholdPx = Math.max(
                    ViewConfiguration.get(itemView.getContext()).getScaledTouchSlop() * 2f,
                    24f * density
            );

            precisionSeekBar.setOnSeekGestureListener(new PrecisionSeekBarView.OnSeekGestureListener() {
                @Override
                public void onSeekInteractionStart(long anchorPositionMs) {
                    if (activity != null) {
                        activity.setPagerSwipeEnabled(false);
                    }
                    showPlaybackControls(false);
                    beginPreviewScrub();
                    showSeekTimestamp(anchorPositionMs, true);
                    syncBufferingState();
                }

                @Override
                public void onSeekPreview(long positionMs, boolean fineMode, boolean frameStepMode) {
                    showSeekTimestamp(positionMs, true);
                    previewSeekTo(positionMs, frameStepMode);
                }

                @Override
                public void onSeekCommit(long positionMs, boolean wasTap, boolean fineMode, boolean frameStepMode) {
                    commitSeekTo(positionMs, fineMode || frameStepMode, frameStepMode);
                    showSeekTimestamp(positionMs, false);
                    scheduleSeekInfoHide(SEEK_INFO_HIDE_DELAY_MS);
                    scheduleControlsHide(CONTROLS_HIDE_DELAY_MS);
                }

                @Override
                public void onSeekInteractionEnd() {
                    if (activity != null) {
                        activity.setPagerSwipeEnabled(true);
                    }
                    progressHandler.removeCallbacks(previewSeekRunnable);
                    previewDispatchScheduled = false;
                    pendingPreviewPositionMs = Long.MIN_VALUE;
                    if (restorePlaybackAfterScrub && currentPlayer != null) {
                        currentPlayer.play();
                    }
                    restorePlaybackAfterScrub = false;
                    if (!seekInfoHideScheduled) {
                        scheduleSeekInfoHide(250L);
                    }
                    scheduleControlsHide(CONTROLS_HIDE_DELAY_MS);
                    syncProgressUi();
                }
            });

            gestureDetector = new GestureDetector(itemView.getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(@NonNull MotionEvent e) {
                            return true;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                            togglePlaybackControls();
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(@NonNull MotionEvent e) {
                            togglePlayback();
                            return true;
                        }

                        @Override
                        public void onLongPress(@NonNull MotionEvent e) {
                            if (currentPlayer != null && currentPlayer.isPlaying()) {
                                currentPlayer.setPlaybackParameters(new PlaybackParameters(2.0f));
                                showSeekFeedback("2x");
                            }
                        }
                    });

            playerView.setOnTouchListener((v, event) -> {
                if (event == null) {
                    return false;
                }

                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    touchDownX = event.getX();
                    touchDownY = event.getY();
                    drawerSwipeTriggered = false;
                } else if (action == MotionEvent.ACTION_MOVE && !drawerSwipeTriggered) {
                    float dx = event.getX() - touchDownX;
                    float dy = event.getY() - touchDownY;
                    boolean horizontalSwipe = Math.abs(dx) > drawerSwipeThresholdPx
                            && Math.abs(dx) > Math.abs(dy) * 1.2f;
                    if (horizontalSwipe) {
                        drawerSwipeTriggered = true;
                        if (activity != null && isCurrentHolder()) {
                            activity.openVideoListDrawer();
                        }
                        return true;
                    }
                }

                boolean handled = gestureDetector.onTouchEvent(event);
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                        && currentPlayer != null
                        && currentPlayer.getPlaybackParameters().speed != 1.0f) {
                    currentPlayer.setPlaybackParameters(new PlaybackParameters(1.0f));
                }
                return handled;
            });
        }

        void bind() {
            playerView.setUseController(false);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            tvSeekTimestamp.setVisibility(View.GONE);
            tvSeekFeedback.setVisibility(View.GONE);
            precisionSeekBar.setVisibility(View.VISIBLE);
            precisionSeekBar.setAlpha(CONTROLS_HIDDEN_ALPHA);
            precisionSeekBar.setDurationMs(0L);
            precisionSeekBar.setPositionMs(0L);
            precisionSeekBar.setBufferedPositionMs(0L);
            precisionSeekBar.setBuffering(false);
        }

        public void attachPlayer(ExoPlayer player) {
            if (currentPlayer != null && currentPlayer != player) {
                currentPlayer.removeListener(playerListener);
            }
            if (currentPlayer != player) {
                currentPlayer = player;
                currentPlayer.addListener(playerListener);
            }
            if (playerView.getPlayer() != player) {
                playerView.setPlayer(player);
            }
            lastPreviewSeekRealtimeMs = 0L;
            lastPreviewPositionMs = Long.MIN_VALUE;
            pendingPreviewPositionMs = Long.MIN_VALUE;
            previewDispatchScheduled = false;
            restorePlaybackAfterScrub = false;
            hidePlaybackControls();
            syncProgressUi();
            progressHandler.removeCallbacks(progressUpdater);
            progressHandler.post(progressUpdater);
        }

        public void detachPlayer() {
            progressHandler.removeCallbacks(progressUpdater);
            overlayHandler.removeCallbacksAndMessages(null);
            progressHandler.removeCallbacks(previewSeekRunnable);

            if (currentPlayer != null) {
                currentPlayer.removeListener(playerListener);
            }
            if (playerView.getPlayer() != null) {
                playerView.setPlayer(null);
            }
            currentPlayer = null;
            lastPreviewSeekRealtimeMs = 0L;
            lastPreviewPositionMs = Long.MIN_VALUE;
            pendingPreviewPositionMs = Long.MIN_VALUE;
            previewDispatchScheduled = false;
            restorePlaybackAfterScrub = false;

            precisionSeekBar.setDurationMs(0L);
            precisionSeekBar.setPositionMs(0L);
            precisionSeekBar.setBufferedPositionMs(0L);
            precisionSeekBar.setBuffering(false);
            precisionSeekBar.setVisibility(View.VISIBLE);
            precisionSeekBar.setAlpha(CONTROLS_HIDDEN_ALPHA);
            tvSeekTimestamp.setVisibility(View.GONE);
            tvSeekFeedback.setVisibility(View.GONE);

            if (activity != null) {
                activity.setPagerSwipeEnabled(true);
            }
        }

        private void syncProgressUi() {
            if (currentPlayer == null) {
                precisionSeekBar.setDurationMs(0L);
                precisionSeekBar.setPositionMs(0L);
                precisionSeekBar.setBufferedPositionMs(0L);
                precisionSeekBar.setBuffering(false);
                return;
            }

            long duration = sanitizeDuration(currentPlayer.getDuration());
            long position = clamp(currentPlayer.getCurrentPosition(), 0L, duration > 0L ? duration : Long.MAX_VALUE);
            long bufferedPosition = clamp(currentPlayer.getBufferedPosition(), 0L, duration > 0L ? duration : Long.MAX_VALUE);

            precisionSeekBar.setDurationMs(duration);
            precisionSeekBar.setPositionMs(position);
            precisionSeekBar.setBufferedPositionMs(bufferedPosition);
            precisionSeekBar.setFrameStepEnabled(!currentPlayer.isPlaying());
            precisionSeekBar.setFrameDurationMs(33L);

            syncBufferingState();

            if (tvSeekTimestamp.getVisibility() == View.VISIBLE) {
                updateSeekTimestamp(precisionSeekBar.getDisplayedPositionMs(), duration);
            }
        }

        private void syncBufferingState() {
            if (currentPlayer == null) {
                precisionSeekBar.setBuffering(false);
                return;
            }

            long displayedPosition = precisionSeekBar.getDisplayedPositionMs();
            long bufferedPosition = sanitizeDuration(currentPlayer.getBufferedPosition());
            boolean bufferingAtTarget = currentPlayer.isLoading()
                    && bufferedPosition + 250L < displayedPosition;
            precisionSeekBar.setBuffering(bufferingAtTarget);
        }

        private void beginPreviewScrub() {
            if (currentPlayer == null) {
                return;
            }

            restorePlaybackAfterScrub = currentPlayer.isPlaying();
            if (restorePlaybackAfterScrub) {
                currentPlayer.pause();
            }
            currentPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            pendingPreviewPositionMs = Long.MIN_VALUE;
            previewDispatchScheduled = false;
            progressHandler.removeCallbacks(previewSeekRunnable);
        }

        private void previewSeekTo(long positionMs, boolean frameStepMode) {
            if (currentPlayer == null) {
                return;
            }

            long deltaThreshold = frameStepMode ? FRAME_PREVIEW_DELTA_THRESHOLD_MS : PREVIEW_DELTA_THRESHOLD_MS;
            if (pendingPreviewPositionMs != Long.MIN_VALUE
                    && Math.abs(positionMs - pendingPreviewPositionMs) < deltaThreshold) {
                syncBufferingState();
                return;
            }

            pendingPreviewPositionMs = positionMs;
            if (!previewDispatchScheduled) {
                previewDispatchScheduled = true;
                progressHandler.post(previewSeekRunnable);
            }
        }

        private void dispatchLatestPreviewSeek(boolean immediate) {
            if (currentPlayer == null || pendingPreviewPositionMs == Long.MIN_VALUE) {
                return;
            }

            long now = SystemClock.elapsedRealtime();
            long targetPositionMs = pendingPreviewPositionMs;
            if (!immediate && now - lastPreviewSeekRealtimeMs < PREVIEW_MIN_SEEK_INTERVAL_MS) {
                schedulePreviewDispatch(PREVIEW_MIN_SEEK_INTERVAL_MS - (now - lastPreviewSeekRealtimeMs));
                return;
            }

            if (lastPreviewPositionMs != Long.MIN_VALUE
                    && Math.abs(targetPositionMs - lastPreviewPositionMs) < PREVIEW_DELTA_THRESHOLD_MS) {
                syncBufferingState();
                return;
            }

            pendingPreviewPositionMs = Long.MIN_VALUE;
            currentPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            currentPlayer.seekTo(targetPositionMs);
            lastPreviewSeekRealtimeMs = now;
            lastPreviewPositionMs = targetPositionMs;
            syncBufferingState();
        }

        private void commitSeekTo(long positionMs, boolean exact, boolean frameStepMode) {
            if (currentPlayer == null) {
                return;
            }

            progressHandler.removeCallbacks(previewSeekRunnable);
            previewDispatchScheduled = false;
            pendingPreviewPositionMs = Long.MIN_VALUE;
            currentPlayer.setSeekParameters(SeekParameters.EXACT);
            currentPlayer.seekTo(positionMs);
            lastPreviewSeekRealtimeMs = SystemClock.elapsedRealtime();
            lastPreviewPositionMs = positionMs;
            if (restorePlaybackAfterScrub) {
                currentPlayer.play();
            }
            restorePlaybackAfterScrub = false;
            syncBufferingState();
        }

        private void togglePlayback() {
            if (currentPlayer == null) {
                return;
            }
            if (currentPlayer.isPlaying()) {
                currentPlayer.pause();
            } else {
                currentPlayer.play();
            }
            showSeekTimestamp(currentPlayer.getCurrentPosition(), false);
            scheduleSeekInfoHide(500L);
            syncProgressUi();
        }

        private void showPlaybackControls(boolean autoHide) {
            precisionSeekBar.setVisibility(View.VISIBLE);
            precisionSeekBar.setAlpha(CONTROLS_VISIBLE_ALPHA);
            notifyMenuButtonVisibility(true);
            if (autoHide) {
                scheduleControlsHide(CONTROLS_HIDE_DELAY_MS);
            } else {
                overlayHandler.removeCallbacks(hideControlsRunnable);
            }
        }

        private void hidePlaybackControls() {
            precisionSeekBar.setVisibility(View.VISIBLE);
            precisionSeekBar.setAlpha(CONTROLS_HIDDEN_ALPHA);
            notifyMenuButtonVisibility(false);
        }

        private void togglePlaybackControls() {
            if (isPlaybackControlsVisible()) {
                overlayHandler.removeCallbacks(hideControlsRunnable);
                hidePlaybackControls();
                return;
            }
            showPlaybackControls(false);
        }

        private boolean isPlaybackControlsVisible() {
            return precisionSeekBar.getAlpha() > 0.01f;
        }

        private void scheduleControlsHide(long delayMs) {
            overlayHandler.removeCallbacks(hideControlsRunnable);
            overlayHandler.postDelayed(hideControlsRunnable, delayMs);
        }

        private void notifyMenuButtonVisibility(boolean visible) {
            if (activity == null || !isCurrentHolder()) {
                return;
            }
            activity.setMenuButtonVisible(visible);
        }

        private boolean isCurrentHolder() {
            if (activity == null) {
                return false;
            }
            int position = getAdapterPosition();
            return position != RecyclerView.NO_POSITION && activity.isCurrentVideoPosition(position);
        }

        private void showSeekTimestamp(long positionMs, boolean keepVisible) {
            overlayHandler.removeCallbacks(hideSeekInfoRunnable);
            seekInfoHideScheduled = false;
            tvSeekTimestamp.setVisibility(View.VISIBLE);
            updateSeekTimestamp(positionMs, precisionSeekBarDuration());
            if (!keepVisible) {
                scheduleSeekInfoHide(SEEK_INFO_HIDE_DELAY_MS);
            }
        }

        private void updateSeekTimestamp(long positionMs, long durationMs) {
            tvSeekTimestamp.setText(formatTime(positionMs) + " / " + formatTime(durationMs));
        }

        private void scheduleSeekInfoHide(long delayMs) {
            overlayHandler.removeCallbacks(hideSeekInfoRunnable);
            seekInfoHideScheduled = true;
            overlayHandler.postDelayed(hideSeekInfoRunnable, delayMs);
        }

        private void schedulePreviewDispatch(long delayMs) {
            progressHandler.removeCallbacks(previewSeekRunnable);
            previewDispatchScheduled = true;
            progressHandler.postDelayed(previewSeekRunnable, Math.max(PREVIEW_DISPATCH_INTERVAL_MS, delayMs));
        }

        private void showSeekFeedback(@NonNull String text) {
            overlayHandler.removeCallbacks(hideSeekFeedbackRunnable);
            tvSeekFeedback.setText(text);
            tvSeekFeedback.setVisibility(View.VISIBLE);
            overlayHandler.postDelayed(hideSeekFeedbackRunnable, SEEK_FEEDBACK_HIDE_DELAY_MS);
        }

        private long precisionSeekBarDuration() {
            long duration = currentPlayer == null ? 0L : sanitizeDuration(currentPlayer.getDuration());
            return duration > 0L ? duration : 0L;
        }

        private long sanitizeDuration(long durationMs) {
            return durationMs > 0L ? durationMs : 0L;
        }

        private long clamp(long value, long min, long max) {
            return Math.max(min, Math.min(max, value));
        }

        private String formatTime(long millis) {
            long safeMillis = Math.max(0L, millis);
            long totalSeconds = safeMillis / 1000L;
            long minutes = (totalSeconds / 60L) % 60L;
            long seconds = totalSeconds % 60L;
            long hours = totalSeconds / 3600L;

            if (hours > 0L) {
                return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
            }
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }
}
