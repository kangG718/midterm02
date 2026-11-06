package com.example.photoviewer;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PostShareTest {
    @Test
    public void sharesSelectedPostAsPlainText() {
        PostItem post = new PostItem("가족여행", "가족이 최고", "", "https://example.com/photo.jpg");
        Intent intent = PostShare.createIntent(post);
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/plain", intent.getType());
        assertEquals("가족여행", intent.getStringExtra(Intent.EXTRA_SUBJECT));
        assertEquals("가족여행\n\n가족이 최고\n\n사진: https://example.com/photo.jpg",
                intent.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test
    public void omitsEmptyFields() {
        Intent intent = PostShare.createIntent(new PostItem("", "", "", ""));
        assertEquals("제목 없음", intent.getStringExtra(Intent.EXTRA_TEXT));
    }
}
