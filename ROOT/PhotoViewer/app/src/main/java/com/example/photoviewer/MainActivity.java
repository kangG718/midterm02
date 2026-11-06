package com.example.photoviewer;

import android.net.Uri;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String LOCAL_URL = "http://127.0.0.1:8000";
    private static final String CLOUD_URL = "https://garamkang.pythonanywhere.com";
    String site_url = CLOUD_URL;
    TextView textView;
    CloadImage taskDownload;
    PutPost taskUpload;
    private Button loadButton;
    private SwitchCompat serverSwitch;
    private AlertDialog postDialog;
    private EditText titleInput, textInput, usernameInput, passwordInput;
    private ImageView imagePreview;
    private TextView statusText;
    private Button sendButton;
    private Uri imageUri;
    private final List<PostItem> allPosts = new ArrayList<>();
    private EditText searchInput;
    private TextView searchResult;
    private Button searchClear;
    private RecyclerView postList;
    private boolean postsLoaded;
    private boolean newestFirst = true;


    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && postDialog != null && postDialog.isShowing()) {
                    imageUri = uri;
                    imagePreview.setImageURI(uri);
                    statusText.setText("사진을 선택했어요.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        loadButton = findViewById(R.id.btn_load);
        searchInput = findViewById(R.id.search_input);
        searchResult = findViewById(R.id.search_result);
        searchClear = findViewById(R.id.search_clear);
        postList = findViewById(R.id.recyclerView);
        postList.setLayoutManager(new LinearLayoutManager(this));
        findViewById(R.id.open_gallery).setOnClickListener(view -> {
            ArrayList<String> paths = new ArrayList<>();
            for (PostItem post : PostOrder.sort(allPosts, newestFirst)) {
                if (post.imagePath != null && new File(post.imagePath).isFile()) paths.add(post.imagePath);
            }
            Intent intent = new Intent(this, GalleryActivity.class);
            intent.putStringArrayListExtra("image_paths", paths);
            startActivity(intent);
        });
        searchClear.setOnClickListener(view -> {
            searchInput.setText("");
            hideSearchKeyboard();
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                applySearch();
            }

            @Override
            public void afterTextChanged(Editable text) { }
        });
        searchInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                hideSearchKeyboard();
                return true;
            }
            return false;
        });
        RadioGroup sortOrder = findViewById(R.id.sort_order);
        newestFirst = getPreferences(MODE_PRIVATE).getBoolean("newest_first", true);
        sortOrder.check(newestFirst ? R.id.sort_newest : R.id.sort_oldest);
        sortOrder.setOnCheckedChangeListener((group, checkedId) -> {
            newestFirst = checkedId == R.id.sort_newest;
            getPreferences(MODE_PRIVATE).edit().putBoolean("newest_first", newestFirst).apply();
            hideSearchKeyboard();
            applySearch();
        });
        applySearch();
        serverSwitch = findViewById(R.id.server_switch);
        serverSwitch.setOnCheckedChangeListener((button, cloud) -> {
            if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
                taskDownload.cancel(true);
                loadButton.setEnabled(true);
            }
            site_url = cloud ? CLOUD_URL : LOCAL_URL;
            serverSwitch.setText(cloud ? "클라우드 서버" : "로컬 서버 (USB 연결)");
            allPosts.clear();
            postsLoaded = false;
            searchInput.setText("");
            applySearch();
            textView.setText("동기화 버튼을 눌러 주세요.");
        });
    }

    private void hideSearchKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
    }

    private void applySearch() {
        String query = searchInput.getText().toString().trim();
        List<PostItem> visiblePosts = PostOrder.sort(PostSearch.filter(allPosts, query), newestFirst);
        postList.setAdapter(new ImageAdapter(visiblePosts));
        searchClear.setEnabled(searchInput.length() > 0);
        if (!postsLoaded) {
            searchResult.setText("동기화한 게시물에서 검색합니다.");
        } else if (query.isEmpty()) {
            searchResult.setText("전체 " + allPosts.size() + "개");
        } else if (visiblePosts.isEmpty()) {
            searchResult.setText("검색 결과가 없습니다. 다른 검색어를 입력해 주세요.");
        } else {
            searchResult.setText("검색 결과 " + visiblePosts.size() + "개 / 전체 " + allPosts.size() + "개");
        }
    }

    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(site_url + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    public void onClickUpload(View v) {
        if (postDialog != null && postDialog.isShowing()) {
            return;
        }
        View form = getLayoutInflater().inflate(R.layout.dialog_post, null);
        titleInput = form.findViewById(R.id.post_title);
        textInput = form.findViewById(R.id.post_text);
        usernameInput = form.findViewById(R.id.account_name);
        passwordInput = form.findViewById(R.id.account_password);
        imagePreview = form.findViewById(R.id.chosen_image);
        statusText = form.findViewById(R.id.upload_status);
        sendButton = form.findViewById(R.id.send_post);
        imageUri = null;
        postDialog = new AlertDialog.Builder(this)
                .setView(form)
                .setNegativeButton("닫기", null)
                .create();
        form.findViewById(R.id.choose_image).setOnClickListener(
                button -> imagePicker.launch("image/*"));
        sendButton.setOnClickListener(button -> {
            String title = titleInput.getText().toString().trim();
            String text = textInput.getText().toString().trim();
            String user = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString();
            if (title.isEmpty() || text.isEmpty() || imageUri == null
                    || user.isEmpty() || password.isEmpty()) {
                statusText.setText("제목, 내용, 사진과 서버 계정을 모두 입력해 주세요.");
                return;
            }
            if (taskUpload != null && taskUpload.getStatus() == AsyncTask.Status.RUNNING) {
                return;
            }
            taskUpload = new PutPost(title, text, imageUri, user, password);
            taskUpload.execute(site_url + "/api_root/Post/");
        });
        postDialog.show();
        postDialog.setCanceledOnTouchOutside(false);
        Toast.makeText(getApplicationContext(), "Upload", Toast.LENGTH_LONG).show();
    }

    private class CloadImage extends AsyncTask<String, Integer, List<PostItem>> {
        private String errorMessage;
        private int unavailableImages;

        @Override
        protected void onPreExecute() {
            loadButton.setEnabled(false);
            textView.setText("동기화 중...");
        }

        @Override
        protected List<PostItem> doInBackground(String... urls) {
            List<PostItem> posts = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(urls[0]).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) throw new IOException("서버 응답: " + responseCode);
                StringBuilder result = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) result.append(line);
                }
                JSONArray aryJson = new JSONArray(result.toString());
                for (int i = 0; i < aryJson.length() && !isCancelled(); i++) {
                    JSONObject json = aryJson.getJSONObject(i);
                    String date = json.isNull("published_date") ? json.optString("created_date", "") : json.optString("published_date", "");
                    PostItem post = new PostItem(json.optString("title", ""), json.optString("text", ""), date, json.optString("image", ""));
                    if (!post.imageUrl.isEmpty()) {
                        try {
                            File photo = PostImages.download(new File(getCacheDir(), "post_images"), new URL(new URL(urls[0]), post.imageUrl).toString());
                            post.imagePath = photo.getAbsolutePath();
                            post.thumbnail = PostImages.decode(post.imagePath, 800);
                        } catch (Exception error) {
                            unavailableImages++;
                        }
                    }
                    posts.add(post);
                }
            } catch (Exception error) {
                errorMessage = "동기화 실패: " + error.getMessage();
            } finally {
                if (conn != null) conn.disconnect();
            }
            return posts;
        }

        @Override
        protected void onPostExecute(List<PostItem> posts) {
            if (taskDownload != this || isFinishing() || isDestroyed()) return;
            loadButton.setEnabled(true);
            if (errorMessage != null) {
                textView.setText(errorMessage);
                return;
            }
            String status = posts.isEmpty() ? "불러올 게시물이 없습니다." : "이미지 로드 성공! 게시물을 눌러 자세히 보세요.";
            if (unavailableImages > 0) status += "\n사진 " + unavailableImages + "개를 불러오지 못했습니다.";
            textView.setText(status);
            allPosts.clear();
            allPosts.addAll(posts);
            postsLoaded = true;
            applySearch();
        }
    }

    private class PutPost extends AsyncTask<String, Void, Void> {
        private final String title, text, user, password;
        private final Uri photo;
        private String result;
        private boolean uploaded;

        PutPost(String title, String text, Uri photo, String user, String password) {
            this.title = title;
            this.text = text;
            this.photo = photo;
            this.user = user;
            this.password = password;
        }

        @Override
        protected void onPreExecute() {
            sendButton.setEnabled(false);
            serverSwitch.setEnabled(false);
            statusText.setText("게시 중...");
            postDialog.setCancelable(false);
            postDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        }

        @Override
        protected Void doInBackground(String... urls) {
            HttpURLConnection conn = null;
            try {
                String token = logIn(user, password);
                String boundary = "PhotoViewer" + UUID.randomUUID();
                URL urlAPI = new URL(urls[0]);
                conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream output = conn.getOutputStream()) {
                    writeText(output, boundary, "title", title);
                    writeText(output, boundary, "text", text);
                    writeImage(output, boundary, photo);
                    output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int responseCode = conn.getResponseCode();
                uploaded = responseCode == HttpURLConnection.HTTP_CREATED;
                result = uploaded ? "게시 완료! 닫기를 누르고 동기화해 주세요."
                        : "게시 실패: 서버 응답 " + responseCode;
            } catch (Exception error) {
                result = "게시 실패: " + error.getMessage();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            statusText.setText(result);
            sendButton.setEnabled(true);
            serverSwitch.setEnabled(true);
            postDialog.setCancelable(true);
            postDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
            if (uploaded) {
                passwordInput.getText().clear();
                textView.setText("게시 완료! 동기화 버튼을 눌러 주세요.");
            }
            taskUpload = null;
        }
    }

    private String logIn(String username, String password) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL(site_url + "/api-token-auth/").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        String form = "username=" + URLEncoder.encode(username, "UTF-8")
                + "&password=" + URLEncoder.encode(password, "UTF-8");
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(form.getBytes(StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("로그인 정보를 확인해 주세요.");
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            return new JSONObject(response.toString()).getString("token");
        } finally {
            connection.disconnect();
        }
    }

    private void writeText(OutputStream output, String boundary, String name, String value)
            throws IOException {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeImage(OutputStream output, String boundary, Uri uri)
            throws IOException {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType == null) {
            mimeType = "image/jpeg";
        }
        String extension = mimeType.equals("image/png") ? ".png" : ".jpg";
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"photo" + extension + "\"\r\n"
                + "Content-Type: " + mimeType + "\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.UTF_8));

        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("사진을 열 수 없습니다.");
            }
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
        }
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void onDestroy() {
        if (taskDownload != null) {
            taskDownload.cancel(true);
        }
        if (taskUpload != null) {
            taskUpload.cancel(true);
        }
        if (postDialog != null) {
            postDialog.dismiss();
        }
        super.onDestroy();
    }
}
