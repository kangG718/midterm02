package com.example.photoviewer;

import android.content.Intent;

final class PostShare {
    static Intent createIntent(PostItem post) {
        String title = post.title.isEmpty() ? "제목 없음" : post.title;
        StringBuilder message = new StringBuilder(title);
        if (!post.text.isEmpty()) message.append("\n\n").append(post.text);
        if (!post.imageUrl.isEmpty()) message.append("\n\n사진: ").append(post.imageUrl);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, title);
        intent.putExtra(Intent.EXTRA_TEXT, message.toString());
        return intent;
    }
}
