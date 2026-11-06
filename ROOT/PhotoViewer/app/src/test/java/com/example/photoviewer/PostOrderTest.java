package com.example.photoviewer;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class PostOrderTest {
    private PostItem post(String date) { return new PostItem("제목", "본문", date, ""); }

    @Test
    public void ordersInstantsAcrossTimeZonesWithoutChangingSource() {
        PostItem older = post("2026-10-19T10:00:00.123456+09:00");
        PostItem newer = post("2026-10-19T02:00:00Z");
        List<PostItem> source = Arrays.asList(older, newer);
        assertEquals(Arrays.asList(newer, older), PostOrder.sort(source, true));
        assertEquals(Arrays.asList(older, newer), PostOrder.sort(source, false));
        assertEquals(Arrays.asList(older, newer), source);
    }

    @Test
    public void keepsMissingDatesLastAndEqualDatesStable() {
        PostItem missing = post("");
        PostItem invalid = post("invalid");
        PostItem first = post("2026-10-19T10:00:00+09:00");
        PostItem second = post("2026-10-19T10:00:00+09:00");
        for (boolean newest : new boolean[]{true, false}) {
            assertEquals(Arrays.asList(first, second, missing, invalid),
                PostOrder.sort(Arrays.asList(missing, first, invalid, second), newest));
        }
    }
}
