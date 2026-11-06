package com.example.photoviewer;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class PostSearchTest {
    private final PostItem first = new PostItem("미분방정식", "풀어보세요", "", "");
    private final PostItem second = new PostItem("여행", "BUSAN 바다 사진", "", "");
    private final List<PostItem> posts = Arrays.asList(first, second);

    @Test
    public void findsPartialTitleAndBody() {
        assertEquals(Arrays.asList(first), PostSearch.filter(posts, "미분"));
        assertEquals(Arrays.asList(first), PostSearch.filter(posts, "풀어"));
    }

    @Test
    public void ignoresCaseAndSurroundingSpaces() {
        assertEquals(Arrays.asList(second), PostSearch.filter(posts, "  busan  "));
    }

    @Test
    public void emptyQueryRestoresAllWithoutChangingSource() {
        PostSearch.filter(posts, "미분").clear();
        assertEquals(posts, PostSearch.filter(posts, "   "));
        assertEquals(2, posts.size());
    }

    @Test
    public void unmatchedQueryReturnsEmpty() {
        assertTrue(PostSearch.filter(posts, "없는검색어").isEmpty());
    }
}
