package com.example.photoviewer;

import android.widget.Button;
import android.widget.TextView;
import android.graphics.Bitmap;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PostOrderScreenTest {
    private void sync(ActivityScenario<MainActivity> scenario) throws Exception {
        onView(withId(R.id.btn_load)).perform(click());
        for (int i = 0; i < 300; i++) {
            boolean[] done = {false};
            scenario.onActivity(activity -> done[0] = ((Button) activity.findViewById(R.id.btn_load)).isEnabled());
            if (done[0]) break;
            Thread.sleep(100);
        }
        scenario.onActivity(activity -> {
            assertTrue(((TextView) activity.findViewById(R.id.textView)).getText().toString().startsWith("이미지 로드 성공"));
            assertTrue(((RecyclerView) activity.findViewById(R.id.recyclerView)).getAdapter().getItemCount() >= 2);
        });
    }

    private String firstTitle(ActivityScenario<MainActivity> scenario) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        String[] title = {null};
        scenario.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.recyclerView);
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            title[0] = ((TextView) holder.itemView.findViewById(R.id.item_title)).getText().toString();
        });
        return title[0];
    }

    private void capture(String name) throws Exception {
        Thread.sleep(4000);
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        File file = new File(instrumentation.getTargetContext().getExternalFilesDir(null), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
    }

    @Test
    public void sortCloudPostsAndKeepSelection() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            sync(scenario);
            onView(withId(R.id.sort_newest)).perform(click());
            String newest = firstTitle(scenario);
            capture("14-3-newest.png");
            onView(withId(R.id.sort_oldest)).perform(click());
            String oldest = firstTitle(scenario);
            assertNotEquals(newest, oldest);
            capture("14-3-oldest.png");
            onView(withId(R.id.search_input)).perform(replaceText(oldest), closeSoftKeyboard());
            assertEquals(oldest, firstTitle(scenario));
            onView(withId(R.id.search_clear)).perform(click());
            assertEquals(oldest, firstTitle(scenario));
            scenario.recreate();
            onView(withId(R.id.sort_oldest)).check(matches(isChecked()));
            sync(scenario);
            assertEquals(oldest, firstTitle(scenario));
            onView(withId(R.id.sort_newest)).perform(click());
            assertEquals(newest, firstTitle(scenario));
        }
    }
}
