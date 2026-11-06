package com.example.photoviewer;

import android.content.Intent;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.Bitmap;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
public class GalleryTest {
    @Test
    public void emptyGallerySurvivesRecreation() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), GalleryActivity.class);
        try (ActivityScenario<GalleryActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.gallery_empty)).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withId(R.id.gallery_count)).check(matches(withText("동기화한 사진 0장")));
        }
    }

    @Test
    public void cloudPhotosOpenInTwoColumnsAndReturnToSearch() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.btn_load)).perform(click());
            for (int i = 0; i < 300; i++) {
                boolean[] done = {false};
                scenario.onActivity(activity -> done[0] = ((Button) activity.findViewById(R.id.btn_load)).isEnabled());
                if (done[0]) break;
                Thread.sleep(100);
            }
            onView(withId(R.id.search_input)).perform(replaceText("가족"), closeSoftKeyboard());
            onView(withId(R.id.open_gallery)).perform(click());
            onView(withId(R.id.gallery_count)).check(matches(withText("동기화한 사진 2장")));
            Thread.sleep(4000);
            onView(withId(R.id.gallery_grid)).check((view, error) -> {
                if (error != null) throw error;
                RecyclerView grid = (RecyclerView) view;
                assertEquals(2, ((GridLayoutManager) grid.getLayoutManager()).getSpanCount());
                assertEquals(2, grid.getAdapter().getItemCount());
                for (int i = 0; i < 2; i++) {
                    ImageView photo = (ImageView) grid.findViewHolderForAdapterPosition(i).itemView;
                    assertNotNull(photo.getDrawable());
                    assertEquals("사진 " + (i + 1), photo.getContentDescription());
                }
            });
            android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            File target = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "14-5-gallery.png");
            try (FileOutputStream output = new FileOutputStream(target)) {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            screenshot.recycle();
            onView(withId(R.id.gallery_back)).perform(click());
            onView(withId(R.id.search_input)).check(matches(withText("가족")));
        }
    }
}
