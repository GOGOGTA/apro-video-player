package com.example.myapplication;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.List;

public class VideoManageAdapter extends RecyclerView.Adapter<VideoManageAdapter.ManageViewHolder> {

    public interface OnDeleteListener {
        void onDelete(int position);
    }

    private final List<VideoItem> videoList;
    private final OnDeleteListener deleteListener;
    private int deleteModePosition = -1;

    public VideoManageAdapter(List<VideoItem> videoList, OnDeleteListener listener) {
        this.videoList = videoList;
        this.deleteListener = listener;
    }

    public void clearDeleteMode() {
        if (deleteModePosition >= 0 && deleteModePosition < videoList.size()) {
            int old = deleteModePosition;
            deleteModePosition = -1;
            notifyItemChanged(old);
        } else {
            deleteModePosition = -1;
        }
    }

    @NonNull
    @Override
    public ManageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video_manage, parent, false);
        return new ManageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ManageViewHolder holder, int position) {
        holder.bind(videoList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    class ManageViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivThumbnail;
        private final View btnDelete;       // 现在是 MaterialCardView
        private final TextView tvName;

        ManageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivManageThumbnail);
            btnDelete   = itemView.findViewById(R.id.btnManageDelete);
            tvName      = itemView.findViewById(R.id.tvVideoName);
        }

        void bind(VideoItem item, int position) {
            btnDelete.setVisibility(position == deleteModePosition ? View.VISIBLE : View.GONE);

            // 视频名称（去掉扩展名）
            String name = item.getName();
            if (name != null) {
                int dot = name.lastIndexOf('.');
                if (dot > 0) name = name.substring(0, dot);
            }
            tvName.setText(name);
            String accessibleName = name == null || name.trim().isEmpty()
                    ? itemView.getContext().getString(R.string.video_thumbnail)
                    : name;
            ivThumbnail.setContentDescription(accessibleName);
            btnDelete.setContentDescription(itemView.getContext().getString(
                    R.string.hide_video_action,
                    accessibleName
            ));

            // 缩略图
            String path = item.getPath();
            ivThumbnail.setTag(path);

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

            // 长按 → 显示/切换叉号
            ivThumbnail.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return true;

                int oldDel = deleteModePosition;
                deleteModePosition = pos;
                if (oldDel >= 0 && oldDel < videoList.size()) notifyItemChanged(oldDel);
                notifyItemChanged(pos);
                return true;
            });

            // 短按 → 如果有叉号则取消叉号，否则无操作
            ivThumbnail.setOnClickListener(v -> {
                if (deleteModePosition >= 0) {
                    int oldDel = deleteModePosition;
                    deleteModePosition = -1;
                    if (oldDel < videoList.size()) notifyItemChanged(oldDel);
                }
            });

            // 叉号点击 → 删除
            btnDelete.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                deleteModePosition = -1;
                if (deleteListener != null) deleteListener.onDelete(pos);
            });
        }
    }
}
