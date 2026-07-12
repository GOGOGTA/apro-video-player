package com.example.myapplication;

public class VideoItem {
    private final String path;
    private final String name;
    private final long sizeBytes;

    public VideoItem(String path, String name, long sizeBytes) {
        this.path = path;
        this.name = name;
        this.sizeBytes = sizeBytes;
    }

    public String getPath() { return path; }
    public String getName() { return name; }
    public long getSizeBytes() { return sizeBytes; }
}
