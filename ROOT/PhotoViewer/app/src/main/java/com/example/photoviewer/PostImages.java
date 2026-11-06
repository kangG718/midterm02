package com.example.photoviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class PostImages {
    static File download(File directory, String address) throws Exception {
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("사진 저장 공간을 만들 수 없습니다.");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte value : digest) name.append(String.format("%02x", value & 0xff));
        File destination = new File(directory, name + ".image");
        File temporary = File.createTempFile("photo", ".part", directory);
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("사진을 불러올 수 없습니다.");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("취소됨");
                    output.write(buffer, 0, count);
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temporary.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("지원하지 않는 사진입니다.");
            if (!temporary.renameTo(destination)) throw new IOException("사진을 저장하지 못했습니다.");
            return destination;
        } finally {
            connection.disconnect();
            temporary.delete();
        }
    }

    static Bitmap decode(String path, int longestSide) {
        if (path == null) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > longestSide) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }
}
