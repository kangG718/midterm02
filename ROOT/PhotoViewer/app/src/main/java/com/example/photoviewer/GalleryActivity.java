package com.example.photoviewer;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryActivity extends AppCompatActivity {
    private final ExecutorService loader = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.gallery_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        findViewById(R.id.gallery_back).setOnClickListener(view -> finish());
        ArrayList<String> paths = getIntent().getStringArrayListExtra("image_paths");
        if (paths == null) paths = new ArrayList<>();
        ((TextView) findViewById(R.id.gallery_count)).setText("동기화한 사진 " + paths.size() + "장");
        findViewById(R.id.gallery_empty).setVisibility(paths.isEmpty() ? View.VISIBLE : View.GONE);
        RecyclerView grid = findViewById(R.id.gallery_grid);
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setAdapter(new GalleryAdapter(paths));
    }

    private class GalleryAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private final ArrayList<String> paths;
        GalleryAdapter(ArrayList<String> paths) { this.paths = paths; }

        @NonNull
        @Override
        public PhotoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView image = new AppCompatImageView(parent.getContext()) {
                @Override
                protected void onMeasure(int widthSpec, int heightSpec) {
                    int side = MeasureSpec.getSize(widthSpec);
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY));
                }
            };
            image.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            int padding = (int) (4 * getResources().getDisplayMetrics().density);
            image.setPadding(padding, padding, padding, padding);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new PhotoHolder(image);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoHolder holder, int position) {
            String path = paths.get(position);
            holder.image.setTag(path);
            holder.image.setContentDescription("사진 " + (position + 1));
            Bitmap saved = cache.get(path);
            holder.image.setImageBitmap(saved);
            if (saved != null) return;
            loader.execute(() -> {
                Bitmap bitmap = PostImages.decode(path, 800);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (bitmap != null) cache.put(path, bitmap);
                    if (!path.equals(holder.image.getTag())) return;
                    if (bitmap == null) {
                        holder.image.setImageResource(android.R.drawable.ic_menu_report_image);
                        holder.image.setContentDescription("사진을 불러올 수 없습니다. 다시 동기화해 주세요.");
                    } else {
                        holder.image.setImageBitmap(bitmap);
                    }
                });
            });
        }

        @Override
        public void onViewRecycled(@NonNull PhotoHolder holder) {
            holder.image.setTag(null);
            holder.image.setImageDrawable(null);
        }

        @Override
        public int getItemCount() { return paths.size(); }
    }

    private static class PhotoHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        PhotoHolder(ImageView image) { super(image); this.image = image; }
    }

    @Override
    protected void onDestroy() {
        loader.shutdownNow();
        cache.evictAll();
        super.onDestroy();
    }
}
