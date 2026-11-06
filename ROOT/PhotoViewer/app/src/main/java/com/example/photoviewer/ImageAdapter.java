package com.example.photoviewer;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
    private final List<PostItem> posts;

    public ImageAdapter(List<PostItem> posts) {
        this.posts = posts;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ImageViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        PostItem post = posts.get(position);
        holder.title.setText(post.title.isEmpty() ? "제목 없음" : post.title);
        holder.date.setText(PostItem.formatDate(post.date));
        holder.imageView.setImageBitmap(post.thumbnail);
        holder.imageView.setVisibility(post.thumbnail == null ? View.GONE : View.VISIBLE);
        holder.share.setOnClickListener(view -> view.getContext().startActivity(
                Intent.createChooser(PostShare.createIntent(post), "게시물 공유")));
        holder.itemView.setOnClickListener(view -> {
            Intent intent = new Intent(view.getContext(), PostDetailActivity.class);
            intent.putExtra("title", post.title);
            intent.putExtra("text", post.text);
            intent.putExtra("date", post.date);
            intent.putExtra("image_path", post.imagePath);
            view.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;
        final TextView title;
        final TextView date;
        final Button share;

        ImageViewHolder(@NonNull View view) {
            super(view);
            imageView = view.findViewById(R.id.imageViewItem);
            title = view.findViewById(R.id.item_title);
            date = view.findViewById(R.id.item_date);
            share = view.findViewById(R.id.item_share);
        }
    }
}
