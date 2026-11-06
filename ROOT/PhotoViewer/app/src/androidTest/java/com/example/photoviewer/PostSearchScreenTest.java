package com.example.photoviewer;

import android.widget.Button;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import androidx.test.platform.app.InstrumentationRegistry;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PostSearchScreenTest {
    private void capture(String name) throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        Thread.sleep(4000);
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        File file = new File(instrumentation.getTargetContext().getExternalFilesDir(null), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        screenshot.recycle();
    }

    @Test
    public void searchCloudPostsAndRestoreList() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.btn_load)).perform(click());
            for (int i = 0; i < 200; i++) {
                boolean[] done = {false};
                scenario.onActivity(activity -> done[0] = ((Button) activity.findViewById(R.id.btn_load)).isEnabled());
                if (done[0]) break;
                Thread.sleep(100);
            }
            scenario.onActivity(activity -> assertTrue(((TextView) activity.findViewById(R.id.textView)).getText().toString().startsWith("이미지 로드 성공")));
            onView(withId(R.id.search_input)).perform(replaceText("미분"), closeSoftKeyboard());
            onView(withId(R.id.item_title)).check(matches(withText("미분방정식")));
            capture("14-2-search-match.png");
            onView(withId(R.id.search_input)).perform(replaceText("풀어"), closeSoftKeyboard());
            onView(withId(R.id.item_title)).check(matches(withText("미분방정식")));
            onView(withId(R.id.search_input)).perform(replaceText("없는검색어"), closeSoftKeyboard());
            onView(withId(R.id.search_result)).check(matches(withText("검색 결과가 없습니다. 다른 검색어를 입력해 주세요.")));
            scenario.onActivity(activity -> assertEquals(0, ((RecyclerView) activity.findViewById(R.id.recyclerView)).getAdapter().getItemCount()));
            capture("14-2-search-empty.png");
            onView(withId(R.id.search_clear)).perform(click());
            onView(withId(R.id.search_input)).check(matches(withText("")));
            scenario.onActivity(activity -> assertTrue(((RecyclerView) activity.findViewById(R.id.recyclerView)).getAdapter().getItemCount() >= 2));
            onView(withId(R.id.search_input)).perform(replaceText("미분"), closeSoftKeyboard());
            onView(withId(R.id.item_title)).perform(click());
            onView(withId(R.id.detail_title)).check(matches(withText("미분방정식")));
            onView(withId(R.id.detail_back)).perform(click());
            onView(withId(R.id.search_input)).check(matches(withText("미분")));
            onView(withId(R.id.server_switch)).perform(click());
            scenario.onActivity(activity -> assertEquals(0, ((RecyclerView) activity.findViewById(R.id.recyclerView)).getAdapter().getItemCount()));
            onView(withId(R.id.search_input)).check(matches(withText("")));
        }
    }
}
