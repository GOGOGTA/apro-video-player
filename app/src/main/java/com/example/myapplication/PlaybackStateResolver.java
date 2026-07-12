package com.example.myapplication;

import java.util.List;

/** Pure list-state logic kept separate from Android lifecycle code so it can be unit tested. */
final class PlaybackStateResolver {

    private PlaybackStateResolver() {
    }

    static int resolveIndex(List<VideoItem> videos, String preferredPath, int fallbackIndex) {
        if (videos == null || videos.isEmpty()) {
            return -1;
        }
        if (preferredPath != null) {
            for (int i = 0; i < videos.size(); i++) {
                if (preferredPath.equals(videos.get(i).getPath())) {
                    return i;
                }
            }
        }
        return Math.max(0, Math.min(fallbackIndex, videos.size() - 1));
    }

    static boolean isSameVideo(List<VideoItem> videos, int index, String preferredPath) {
        return preferredPath != null
                && videos != null
                && index >= 0
                && index < videos.size()
                && preferredPath.equals(videos.get(index).getPath());
    }
}
