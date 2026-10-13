package com.petshop.activity;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.petshop.R;
import com.petshop.adapter.CommunityCommentAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AuthUtil;
import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 动态评论页：分页加载评论 + 底部输入框发送
 */
public class PostCommentsActivity extends AppCompatActivity {

    private static final String TAG = "PostComments";
    /** 单页条数，与后端 PageHelper 配合，滑到底部自动加载下一页 */
    private static final int PAGE_SIZE = 20;

    private RecyclerView rvComments;
    private TextView tvEmpty;
    private EditText etComment;
    private ImageView btnSend;

    private CommunityCommentAdapter adapter;
    private final List<ApiService.CommunityCommentItem> comments = new ArrayList<>();
    /** 去重：本地刚发的评论只有临时 id，翻到最后一页时可能和服务端数据撞车 */
    private final Set<Integer> loadedIds = new HashSet<>();

    private int postId;
    private int currentPage = 1;
    private boolean loading = false;
    private boolean hasMore = true;
    private boolean sending = false;
    /** 发过评论就通知列表页刷新评论数 */
    private boolean commented = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_comments);

        postId = getIntent().getIntExtra("postId", -1);
        if (postId <= 0) {
            finish();
            return;
        }

        initView();
        loadComments();
    }

    private void initView() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        String authorName = getIntent().getStringExtra("authorName");
        if (authorName != null && !authorName.isEmpty()) {
            toolbar.setSubtitle(authorName);
        }

        rvComments = findViewById(R.id.rv_comments);
        tvEmpty = findViewById(R.id.tv_empty);
        etComment = findViewById(R.id.et_comment);
        btnSend = findViewById(R.id.btn_send);

        adapter = new CommunityCommentAdapter(this, comments);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        rvComments.setAdapter(adapter);

        // 滑到接近底部时自动加载下一页
        rvComments.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (!(layoutManager instanceof LinearLayoutManager)) {
                    return;
                }
                int lastVisible = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
                if (lastVisible >= adapter.getItemCount() - 3) {
                    loadMoreComments();
                }
            }
        });

        btnSend.setOnClickListener(v -> sendComment());
    }

    private void loadMoreComments() {
        if (loading || !hasMore) {
            return;
        }
        currentPage++;
        loadComments();
    }

    private void loadComments() {
        if (loading || !hasMore) {
            return;
        }
        loading = true;
        final int page = currentPage;
        ApiClient.getInstance(this).getApiService().getCommunityComments(postId, page, PAGE_SIZE)
                .enqueue(new Callback<ApiService.Response<List<ApiService.CommunityCommentItem>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.CommunityCommentItem>>> call,
                                           Response<ApiService.Response<List<ApiService.CommunityCommentItem>>> response) {
                        loading = false;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200 && response.body().getData() != null) {
                            List<ApiService.CommunityCommentItem> pageData = response.body().getData();
                            if (pageData.size() < PAGE_SIZE) {
                                hasMore = false;
                            }
                            appendPage(pageData);
                        } else {
                            if (page > 1) {
                                currentPage--; // 回退页码，允许重新触发加载
                            }
                            String message = response.body() == null ? "评论加载失败" : response.body().getMessage();
                            ToastUtil.showError(PostCommentsActivity.this, message);
                        }
                        updateEmptyState();
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.CommunityCommentItem>>> call,
                                          Throwable t) {
                        loading = false;
                        if (page > 1) {
                            currentPage--;
                        }
                        LogUtil.e(TAG, "评论加载失败", t);
                        ToastUtil.showError(PostCommentsActivity.this, "网络异常，请稍后重试");
                        updateEmptyState();
                    }
                });
    }

    private void appendPage(List<ApiService.CommunityCommentItem> pageData) {
        int before = comments.size();
        for (ApiService.CommunityCommentItem item : pageData) {
            if (item.getId() > 0 && !loadedIds.add(item.getId())) {
                continue;
            }
            if (item.getId() > 0 && isLocallyAppended(item)) {
                continue;
            }
            comments.add(item);
        }
        if (comments.size() > before) {
            adapter.notifyItemRangeInserted(before, comments.size() - before);
        }
        // 只有一页时直接落到最新一条，免得多划一次
        if (before == 0 && !hasMore && !comments.isEmpty()) {
            rvComments.scrollToPosition(comments.size() - 1);
        }
    }

    /** 本地刚发的评论 id 为负数，服务端分页里迟早会出现同一条，命中就不再插入 */
    private boolean isLocallyAppended(ApiService.CommunityCommentItem serverItem) {
        for (ApiService.CommunityCommentItem local : comments) {
            if (local.getId() < 0 && serverItem.getContent() != null
                    && serverItem.getContent().equals(local.getContent())) {
                return true;
            }
        }
        return false;
    }

    private void sendComment() {
        if (sending) {
            return;
        }
        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            return;
        }
        String text = etComment.getText().toString().trim();
        if (text.isEmpty()) {
            ToastUtil.showWarning(this, "评论内容不能为空");
            return;
        }

        sending = true;
        btnSend.setEnabled(false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("content", text);
        ApiClient.getInstance(this).getApiService().addCommunityComment(postId, payload)
                .enqueue(new Callback<ApiService.Response<Integer>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Integer>> call,
                                           Response<ApiService.Response<Integer>> response) {
                        sending = false;
                        btnSend.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            etComment.setText("");
                            hideKeyboard();
                            appendLocalComment(text);
                            commented = true;
                            ToastUtil.showSuccess(PostCommentsActivity.this, "评论成功");
                        } else {
                            String message = response.body() == null ? "评论失败" : response.body().getMessage();
                            ToastUtil.showError(PostCommentsActivity.this, message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Integer>> call, Throwable t) {
                        sending = false;
                        btnSend.setEnabled(true);
                        LogUtil.e(TAG, "评论失败", t);
                        ToastUtil.showError(PostCommentsActivity.this, "网络异常，请稍后重试");
                    }
                });
    }

    private void appendLocalComment(String text) {
        ApiService.CommunityCommentItem item = new ApiService.CommunityCommentItem();
        item.setId(-1);
        item.setUserId(AuthUtil.getCurrentUserId(this));
        item.setUserName("我");
        item.setContent(text);
        item.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));

        int before = comments.size();
        comments.add(item);
        adapter.notifyItemInserted(before);
        rvComments.scrollToPosition(before);
        updateEmptyState();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        focus.clearFocus();
    }

    private void updateEmptyState() {
        boolean empty = comments.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void finish() {
        if (commented) {
            setResult(Activity.RESULT_OK);
        }
        super.finish();
    }
}
