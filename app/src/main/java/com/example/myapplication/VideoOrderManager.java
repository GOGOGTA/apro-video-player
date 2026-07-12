package com.example.myapplication;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class VideoOrderManager {

    private static final String TAG = "VideoOrderManager";
    private static final String FILE_NAME = "video_order.json";

    private final AtomicFile orderFile;

    public VideoOrderManager(Context context) {
        Context appContext = context.getApplicationContext();
        Context safeContext = appContext != null ? appContext : context;
        orderFile = new AtomicFile(new File(safeContext.getFilesDir(), FILE_NAME));
    }

    /**
     * Persists the complete order atomically so a process stop or storage failure cannot leave a
     * truncated JSON file behind.
     */
    public synchronized void saveOrder(List<VideoItem> videoList) {
        JSONArray array = new JSONArray();
        for (VideoItem item : videoList) {
            array.put(item.getPath());
        }

        FileOutputStream output = null;
        try {
            output = orderFile.startWrite();
            output.write(array.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            orderFile.finishWrite(output);
        } catch (Exception e) {
            if (output != null) {
                orderFile.failWrite(output);
            }
            Log.w(TAG, "Unable to save the video order", e);
        }
    }

    public synchronized List<String> loadOrder() {
        List<String> order = new ArrayList<>();
        try (FileInputStream input = orderFile.openRead();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }

            JSONArray array = new JSONArray(json.toString());
            for (int i = 0; i < array.length(); i++) {
                order.add(array.getString(i));
            }
        } catch (FileNotFoundException ignored) {
            // First launch: no order has been saved yet.
        } catch (Exception e) {
            Log.w(TAG, "Unable to load the video order", e);
            order.clear();
        }
        return order;
    }
}
