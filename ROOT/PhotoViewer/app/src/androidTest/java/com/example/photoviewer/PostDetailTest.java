package com.example.photoviewer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class PostDetailTest {
    private Intent detailIntent(String path) {
        Context context = ApplicationProvider.getApplicationContext();
        return new Intent(context, PostDetailActivity.class)
                .putExtra("title", "상세보기 검증")
                .putExtra("text", "게시물 본문 확인")
                .putExtra("date", "2026-10-14T02:49:53.916955+09:00")
                .putExtra("image_path", path);
    }

    @Test
    public void detailShowsPhotoAndReturns() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File file = new File(context.getCacheDir(), "detail-test.png");
        Bitmap bitmap = Bitmap.createBitmap(1000, 600, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
        try (ActivityScenario<PostDetailActivity> scenario = ActivityScenario.launch(detailIntent(file.getPath()))) {
            onView(withId(R.id.detail_title)).check(matches(withText("상세보기 검증")));
            onView(withId(R.id.detail_text)).perform(scrollTo()).check(matches(withText("게시물 본문 확인")));
            for (int i = 0; i < 50; i++) {
                final boolean[] ready = {false};
                scenario.onActivity(activity -> ready[0] = ((android.widget.ImageView) activity.findViewById(R.id.detail_image)).getDrawable() != null);
                if (ready[0]) break;
                Thread.sleep(100);
            }
            onView(withId(R.id.detail_image)).perform(scrollTo()).check(matches(isDisplayed()));
            scenario.onActivity(activity -> assertTrue(((android.widget.ImageView) activity.findViewById(R.id.detail_image)).getDrawable() != null));
            onView(withId(R.id.detail_back)).perform(click());
            assertTrue(scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
        } finally {
            file.delete();
        }
    }

    @Test
    public void missingImagePreservesPostText() {
        try (ActivityScenario<PostDetailActivity> scenario = ActivityScenario.launch(detailIntent(null))) {
            onView(withId(R.id.detail_title)).check(matches(withText("상세보기 검증")));
            onView(withId(R.id.detail_text)).perform(scrollTo()).check(matches(withText("게시물 본문 확인")));

        }
    }
}
