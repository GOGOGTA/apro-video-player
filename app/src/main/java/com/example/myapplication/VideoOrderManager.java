package com.example.myapplication;

import android.content.Context;
import org.json.JSONArray;
import java.io.*;
import java.util.*;

public class VideoOrderManager {
    private static final String FILE_NAME = "video_order.json";
    private final File orderFile;
    private List<String> cachedOrder = new ArrayList<>();
    private long cachedLastModified = -1L;

    public VideoOrderManager(Context context) {
        orderFile = new File(context.getFilesDir(), FILE_NAME);
    }

    public void saveOrder(List<VideoItem> videoList) {
        try {
            JSONArray array = new JSONArray();
            for (VideoItem item : videoList) array.put(item.getPath());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(orderFile))) {
                writer.write(array.toString());
            }
            cachedOrder = new ArrayList<>();
            for (VideoItem item : videoList) {
                cachedOrder.add(item.getPath());
            }
            cachedLastModified = orderFile.lastModified();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> loadOrder() {
        if (!orderFile.exists()) return new ArrayList<>();
        long lastModified = orderFile.lastModified();
        if (cachedLastModified == lastModified) {
            return new ArrayList<>(cachedOrder);
        }
        List<String> order = new ArrayList<>();
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(orderFile))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) order.add(array.getString(i));
            cachedOrder = new ArrayList<>(order);
            cachedLastModified = lastModified;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return order;
    }
}
