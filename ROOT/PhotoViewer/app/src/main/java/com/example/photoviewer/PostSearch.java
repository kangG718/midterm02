package com.example.photoviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PostSearch {
    static List<PostItem> filter(List<PostItem> posts, String query) {
        String keyword = query.trim().toLowerCase(Locale.ROOT);
        List<PostItem> matches = new ArrayList<>();
        for (PostItem post : posts) {
            if (keyword.isEmpty() || post.title.toLowerCase(Locale.ROOT).contains(keyword)
                    || post.text.toLowerCase(Locale.ROOT).contains(keyword)) {
                matches.add(post);
            }
        }
        return matches;
    }
}
