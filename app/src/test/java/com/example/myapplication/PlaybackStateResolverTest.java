package com.example.myapplication;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackStateResolverTest {

    private final List<VideoItem> videos = Arrays.asList(
            new VideoItem("content://video/1", "one.mp4", 1L),
            new VideoItem("content://video/2", "two.mp4", 2L),
            new VideoItem("content://video/3", "three.mp4", 3L)
    );

    @Test
    public void preferredVideoWinsAfterReorder() {
        assertEquals(1, PlaybackStateResolver.resolveIndex(videos, "content://video/2", 0));
        assertTrue(PlaybackStateResolver.isSameVideo(videos, 1, "content://video/2"));
    }

    @Test
    public void removedVideoUsesClampedFallbackWithoutRestoringPosition() {
        assertEquals(2, PlaybackStateResolver.resolveIndex(videos, "content://video/9", 8));
        assertFalse(PlaybackStateResolver.isSameVideo(videos, 2, "content://video/9"));
    }

    @Test
    public void initialAndEmptyListsAreHandledSafely() {
        assertEquals(0, PlaybackStateResolver.resolveIndex(videos, null, -1));
        assertEquals(-1, PlaybackStateResolver.resolveIndex(Collections.emptyList(), null, 0));
    }
}
