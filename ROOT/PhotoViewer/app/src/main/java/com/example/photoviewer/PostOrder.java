package com.example.photoviewer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class PostOrder {
    static List<PostItem> sort(List<PostItem> posts, boolean newestFirst) {
        List<PostItem> sorted = new ArrayList<>(posts);
        sorted.sort((left, right) -> {
            Long first = timestamp(left.date);
            Long second = timestamp(right.date);
            if (first == null) return second == null ? 0 : 1;
            if (second == null) return -1;
            return newestFirst ? Long.compare(second, first) : Long.compare(first, second);
        });
        return sorted;
    }

    private static Long timestamp(String value) {
        if (value == null || value.isEmpty()) return null;
        String normalized = value.replaceFirst("(\\.\\d{3})\\d+", "$1");
        for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX"}) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                Date parsed = format.parse(normalized);
                if (parsed != null) return parsed.getTime();
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
