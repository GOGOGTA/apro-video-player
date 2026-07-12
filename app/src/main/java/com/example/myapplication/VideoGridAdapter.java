package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.List;

public class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.GridViewHolder> {

    /* ==================== 接口 ==================== */

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnDragStartListener {
        void onDragStart(RecyclerView.ViewHolder holder);
    }

    /* ==================== 字段 ==================== */

    private final List<VideoItem> videoList;
    private final OnItemClickListener listener;
    private OnDragStartListener dragStartListener;
    private int selectedPosition = 0;

    private static final long LONG_PRESS_DELAY = 450;
    private static final float DRAG_THRESHOLD = 12f;

    /* ==================== 构造 & Setter ==================== */

    public VideoGridAdapter(List<VideoItem> videoList, OnItemClickListener listener) {
        this.videoList = videoList;
        this.listener = listener;
    }

    public void setOnDragStartListener(OnDragStartListener l) {
        this.dragStartListener = l;
    }

    /* ==================== Adapter 标准方法 ==================== */

    @NonNull
    @Override
    public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video_grid, parent, false);
        return new GridViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
        holder.bind(videoList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    @Override
    public void onViewRecycled(@NonNull GridViewHolder holder) {
        holder.cancelPendingInteraction();
        super.onViewRecycled(holder);
    }

    /* ==================== 数据操作 ==================== */

    public void updateData() {
        selectedPosition = 0;
        notifyDataSetChanged();
    }

    public void setSelectedPosition(int position) {
        int old = selectedPosition;
        selectedPosition = position;
        if (old >= 0 && old < videoList.size()) notifyItemChanged(old);
        if (selectedPosition >= 0 && selectedPosition < videoList.size()) {
            notifyItemChanged(selectedPosition);
        }
    }

    public void onItemMove(int from, int to) {
        VideoItem moved = videoList.remove(from);
        videoList.add(to, moved);
        notifyItemMoved(from, to);

        if (selectedPosition == from) {
            selectedPosition = to;
        } else if (from < selectedPosition && to >= selectedPosition) {
            selectedPosition--;
        } else if (from > selectedPosition && to <= selectedPosition) {
            selectedPosition++;
        }
    }

    /* ==================== ViewHolder ==================== */

    class GridViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivThumbnail;
        private final View selectedOverlay;

        private final Handler longPressHandler = new Handler(Looper.getMainLooper());

        private boolean longPressTriggered = false;
        private boolean dragStarted = false;
        private float downX, downY;

        public GridViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail     = itemView.findViewById(R.id.ivThumbnail);
            selectedOverlay = itemView.findViewById(R.id.selectedOverlay);
            ivThumbnail.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(position);
                }
            });
        }

        void bind(VideoItem item, int position) {
            /* ---------- 视觉状态 ---------- */
            selectedOverlay.setVisibility(position == selectedPosition ? View.VISIBLE : View.GONE);

            /* ---------- 缩略图 ---------- */
            String path = item.getPath();
            ivThumbnail.setTag(path);
            String itemName = item.getName();
            ivThumbnail.setContentDescription(
                    itemName == null || itemName.trim().isEmpty()
                            ? itemView.getContext().getString(R.string.video_thumbnail)
                            : itemName
            );

            Bitmap cached = ThumbnailCacheManager.getInstance().getCached(path);
            if (cached != null) {
                ivThumbnail.setImageBitmap(cached);
            } else {
                ivThumbnail.setImageResource(android.R.color.darker_gray);
                WeakReference<ImageView> thumbnailRef = new WeakReference<>(ivThumbnail);
                ThumbnailCacheManager.getInstance().loadAsync(
                        itemView.getContext(), path, (loadedPath, bitmap) -> {
                            ImageView thumbnail = thumbnailRef.get();
                            if (thumbnail != null
                                    && loadedPath.equals(thumbnail.getTag())
                                    && bitmap != null) {
                                thumbnail.setImageBitmap(bitmap);
                            }
                        });
            }

            /* ==========================================================
             *  触摸交互（只在 ivThumbnail 上监听）：
             *
             *  短按（<450ms 松手）→ 点击跳转播放
             *  长按后拖动         → 拖拽排序
             * ========================================================== */
            ivThumbnail.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        longPressTriggered = false;
                        dragStarted = false;

                        longPressHandler.postDelayed(() -> {
                            longPressTriggered = true;
                            vibrateShort(v.getContext());
                            itemView.animate()
                                    .scaleX(0.87f).scaleY(0.87f)
                                    .setDuration(160).start();
                        }, LONG_PRESS_DELAY);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(event.getRawX() - downX);
                        float dy = Math.abs(event.getRawY() - downY);

                        if (!longPressTriggered) {
                            if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                                longPressHandler.removeCallbacksAndMessages(null);
                                return false;
                            }
                        } else if (!dragStarted) {
                            if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                                dragStarted = true;
                                if (dragStartListener != null) {
                                    dragStartListener.onDragStart(GridViewHolder.this);
                                }
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        longPressHandler.removeCallbacksAndMessages(null);

                        int pos = getAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) {
                            resetScale();
                            return true;
                        }

                        if (!longPressTriggered) {
                            /* ---- 普通短按 → 跳转播放 ---- */
                            v.performClick();
                        }
                        resetScale();
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        longPressHandler.removeCallbacksAndMessages(null);
                        if (!dragStarted) {
                            resetScale();
                        }
                        return true;
                }
                return false;
            });

            /* 防止系统长按弹出分享框 */
            ivThumbnail.setOnLongClickListener(v -> true);
            ivThumbnail.setHapticFeedbackEnabled(false);
        }

        private void resetScale() {
            itemView.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        }

        private void cancelPendingInteraction() {
            longPressHandler.removeCallbacksAndMessages(null);
            itemView.animate().cancel();
            itemView.setScaleX(1f);
            itemView.setScaleY(1f);
            longPressTriggered = false;
            dragStarted = false;
        }

        private void vibrateShort(Context context) {
            try {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator == null) return;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(30);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
