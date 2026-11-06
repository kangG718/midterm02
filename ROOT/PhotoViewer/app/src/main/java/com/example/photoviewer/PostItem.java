package com.example.photoviewer;

import android.graphics.Bitmap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PostItem {
    final String title;
    final String text;
    final String date;
    final String imageUrl;
    String imagePath;
    Bitmap thumbnail;

    PostItem(String title, String text, String date, String imageUrl) {
        this.title = title;
        this.text = text;
        this.date = date;
        this.imageUrl = imageUrl;
    }

    static String formatDate(String value) {
        if (value == null || value.isEmpty()) return "날짜 정보 없음";
        String normalized = value.replaceFirst("(\\.\\d{3})\\d+", "$1");
        for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX"}) {
            try {
                Date date = new SimpleDateFormat(pattern, Locale.US).parse(normalized);
                if (date != null) return new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(date);
            } catch (Exception ignored) {
            }
        }
        return value;
    }
}
