package com.example.photoviewer;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PostDetailActivity extends AppCompatActivity {
    private final ExecutorService imageLoader = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);
        View root = findViewById(R.id.detail_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        findViewById(R.id.detail_back).setOnClickListener(view -> finish());
        String title = getIntent().getStringExtra("title");
        String text = getIntent().getStringExtra("text");
        ((TextView) findViewById(R.id.detail_title)).setText(title == null || title.isEmpty() ? "제목 없음" : title);
        ((TextView) findViewById(R.id.detail_text)).setText(text == null || text.isEmpty() ? "본문이 없습니다." : text);
        ((TextView) findViewById(R.id.detail_date)).setText(PostItem.formatDate(getIntent().getStringExtra("date")));
        ImageView image = findViewById(R.id.detail_image);
        TextView imageStatus = findViewById(R.id.detail_image_status);
        String path = getIntent().getStringExtra("image_path");
        imageLoader.execute(() -> {
            Bitmap decoded = PostImages.decode(path, 2400);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (decoded == null) {
                    image.setVisibility(View.GONE);
                    imageStatus.setText("사진을 불러올 수 없습니다. 목록에서 다시 동기화해 주세요.");
                } else {
                    image.setImageBitmap(decoded);
                    imageStatus.setVisibility(View.GONE);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        imageLoader.shutdownNow();
        super.onDestroy();
    }
}
