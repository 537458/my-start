package com.petshop.fragment;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.petshop.R;
import com.petshop.activity.PostCommentsActivity;
import com.petshop.activity.PublishPostActivity;
import com.petshop.adapter.CommunityPostAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AuthUtil;
import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 宠物社区：广场 / 我的动态两个 Tab，支持点赞、评论、删除与下拉刷新
 */
public class CommunityFragment extends Fragment implements CommunityPostAdapter.Listener {

    private static final String TAG = "CommunityFragment";
    /** 单页条数，与后端 PageHelper 配合，滑到底部自动加载下一页 */
    private static final int PAGE_SIZE = 10;

    private RecyclerView rvPosts;
    private SwipeRefreshLayout srlCommunity;
    private TextView tvTabSquare, tvTabMine, tvEmpty;
    private View indSquare, indMine;
    private EditText etSearch;
    private TextView tvSearchClear;

    private CommunityPostAdapter adapter;
    private final List<ApiService.CommunityPostItem> posts = new ArrayList<>();
    /** 当前生效的搜索关键词，空串表示不过滤 */
    private String keyword = "";

    private ActivityResultLauncher<Intent> publishLauncher;
    private ActivityResultLauncher<Intent> commentsLauncher;

    private boolean showingMine = false;
    private int currentPage = 1;
    private boolean loading = false;
    private boolean hasMore = true;
    /** 切 Tab / 下拉刷新时自增，用来丢弃过期请求的回包 */
    private int requestToken = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 发帖成功后回到列表直接刷新，避免用户手动下拉
        publishLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        reloadPosts();
                    }
                });
        // 评论页可能新增了评论，返回时刷新一下评论数
        commentsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        reloadPosts();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvPosts = view.findViewById(R.id.rv_community);
        srlCommunity = view.findViewById(R.id.srl_community);
        tvTabSquare = view.findViewById(R.id.tv_tab_square);
        tvTabMine = view.findViewById(R.id.tv_tab_mine);
        tvEmpty = view.findViewById(R.id.tv_empty);
        indSquare = view.findViewById(R.id.v_tab_square_indicator);
        indMine = view.findViewById(R.id.v_tab_mine_indicator);
        etSearch = view.findViewById(R.id.et_search);
        tvSearchClear = view.findViewById(R.id.tv_search_clear);

        adapter = new CommunityPostAdapter(requireContext(), posts, this);
        rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvPosts.setAdapter(adapter);

        // 滑到接近底部时自动加载下一页
        rvPosts.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
                    loadMorePosts();
                }
            }
        });

        srlCommunity.setColorSchemeResources(R.color.colorPrimary);
        srlCommunity.setOnRefreshListener(this::reloadPosts);

        view.findViewById(R.id.ll_tab_square).setOnClickListener(v -> switchTab(false));
        view.findViewById(R.id.ll_tab_mine).setOnClickListener(v -> switchTab(true));
        view.findViewById(R.id.fab_publish).setOnClickListener(v -> openPublish());

        // 回车触发搜索，点了清除按钮直接回到不过滤的列表
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyKeyword(etSearch.getText().toString());
                return true;
            }
            return false;
        });
        tvSearchClear.setOnClickListener(v -> {
            etSearch.setText("");
            applyKeyword("");
        });

        updateTabStyle();
        reloadPosts();
    }

    private void switchTab(boolean mine) {
        if (mine && !AuthUtil.checkLoginWithPddStyle(requireContext())) {
            // 未登录时留在广场，登录后再点「我的动态」
            return;
        }
        if (showingMine == mine) {
            if (posts.isEmpty() && !loading) {
                reloadPosts();
            }
            return;
        }
        showingMine = mine;
        updateTabStyle();
        reloadPosts();
    }

    private void updateTabStyle() {
        if (tvTabSquare == null) {
            return;
        }
        tvTabSquare.setTextColor(requireContext().getColor(
                showingMine ? R.color.colorTextSecondary : R.color.colorTextPrimary));
        tvTabMine.setTextColor(requireContext().getColor(
                showingMine ? R.color.colorTextPrimary : R.color.colorTextSecondary));
        tvTabSquare.setTypeface(Typeface.DEFAULT, showingMine ? Typeface.NORMAL : Typeface.BOLD);
        tvTabMine.setTypeface(Typeface.DEFAULT, showingMine ? Typeface.BOLD : Typeface.NORMAL);
        indSquare.setVisibility(showingMine ? View.INVISIBLE : View.VISIBLE);
        indMine.setVisibility(showingMine ? View.VISIBLE : View.INVISIBLE);
    }

    /** 应用搜索关键词：空白表示清空搜索，否则重新拉第一页 */
    private void applyKeyword(String raw) {
        String next = raw == null ? "" : raw.trim();
        etSearch.clearFocus();
        hideKeyboard();
        tvSearchClear.setVisibility(next.isEmpty() ? View.GONE : View.VISIBLE);
        if (next.equals(keyword)) {
            return;
        }
        keyword = next;
        reloadPosts();
    }

    private void hideKeyboard() {
        if (etSearch == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }

    /** 重置分页并从第一页重新加载 */
    private void reloadPosts() {
        requestToken++;
        currentPage = 1;
        hasMore = true;
        loading = false;
        loadPosts();
    }

    private void loadMorePosts() {
        if (loading || !hasMore) {
            return;
        }
        currentPage++;
        loadPosts();
    }

    private void loadPosts() {
        if (!isAdded() || loading || !hasMore) {
            stopRefresh();
            return;
        }
        loading = true;

        final int page = currentPage;
        final int token = requestToken;
        ApiService api = ApiClient.getInstance(requireContext()).getApiService();
        Call<ApiService.Response<List<ApiService.CommunityPostItem>>> call = showingMine
                ? api.getMyCommunityPosts(page, PAGE_SIZE, keyword.isEmpty() ? null : keyword)
                : api.getCommunityPosts(page, PAGE_SIZE, keyword.isEmpty() ? null : keyword);

        call.enqueue(new Callback<ApiService.Response<List<ApiService.CommunityPostItem>>>() {
            @Override
            public void onResponse(Call<ApiService.Response<List<ApiService.CommunityPostItem>>> call,
                                   Response<ApiService.Response<List<ApiService.CommunityPostItem>>> response) {
                if (token != requestToken || !isAdded()) {
                    return;
                }
                loading = false;
                stopRefresh();

                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 200 && response.body().getData() != null) {
                    List<ApiService.CommunityPostItem> pageData = response.body().getData();
                    if (pageData.size() < PAGE_SIZE) {
                        hasMore = false;
                    }
                    if (page == 1) {
                        posts.clear();
                    }
                    posts.addAll(pageData);
                    adapter.notifyDataSetChanged();
                } else {
                    if (page > 1) {
                        currentPage--; // 回退页码，允许重新触发加载
                    }
                    String message = response.body() == null ? "动态加载失败" : response.body().getMessage();
                    ToastUtil.showError(requireContext(), message);
                }
                updateEmptyState();
            }

            @Override
            public void onFailure(Call<ApiService.Response<List<ApiService.CommunityPostItem>>> call,
                                  Throwable t) {
                if (token != requestToken || !isAdded()) {
                    return;
                }
                loading = false;
                stopRefresh();
                if (page > 1) {
                    currentPage--;
                }
                LogUtil.e(TAG, "社区动态加载失败", t);
                ToastUtil.showError(requireContext(), "网络异常，请稍后重试");
                updateEmptyState();
            }
        });
    }

    private void stopRefresh() {
        if (srlCommunity != null) {
            srlCommunity.setRefreshing(false);
        }
    }

    private void updateEmptyState() {
        boolean empty = posts.isEmpty();
        if (!keyword.isEmpty()) {
            tvEmpty.setText("没有找到相关内容\n换个关键词试试");
        } else {
            tvEmpty.setText(showingMine ? "你还没有发过动态" : "还没有动态，来发第一条吧");
        }
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvPosts.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openPublish() {
        if (!AuthUtil.checkLoginWithPddStyle(requireContext())) {
            return;
        }
        publishLauncher.launch(new Intent(requireContext(), PublishPostActivity.class));
    }

    // ---------------- 列表回调 ----------------

    @Override
    public void onLikeClick(ApiService.CommunityPostItem item, int position) {
        if (!AuthUtil.checkLoginWithPddStyle(requireContext())) {
            return;
        }

        final boolean wasLiked = item.isLiked();
        final int oldCount = item.getLikeCount();
        // 先本地翻转，点击立刻有反馈；服务端失败再回滚
        item.setLiked(!wasLiked);
        item.setLikeCount(Math.max(0, oldCount + (wasLiked ? -1 : 1)));
        notifyPostChanged(item);

        ApiService api = ApiClient.getInstance(requireContext()).getApiService();
        Call<ApiService.Response<ApiService.CommunityLikeResult>> call = wasLiked
                ? api.unlikeCommunityPost(item.getId())
                : api.likeCommunityPost(item.getId());

        call.enqueue(new Callback<ApiService.Response<ApiService.CommunityLikeResult>>() {
            @Override
            public void onResponse(Call<ApiService.Response<ApiService.CommunityLikeResult>> call,
                                   Response<ApiService.Response<ApiService.CommunityLikeResult>> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 200 && response.body().getData() != null) {
                    ApiService.CommunityLikeResult result = response.body().getData();
                    item.setLiked(result.isLiked());
                    item.setLikeCount(result.getLikeCount());
                    notifyPostChanged(item);
                } else {
                    rollbackLike(item, wasLiked, oldCount);
                }
            }

            @Override
            public void onFailure(Call<ApiService.Response<ApiService.CommunityLikeResult>> call,
                                  Throwable t) {
                if (!isAdded()) {
                    return;
                }
                rollbackLike(item, wasLiked, oldCount);
                LogUtil.e(TAG, "点赞失败", t);
                ToastUtil.showError(requireContext(), "操作失败，请稍后重试");
            }
        });
    }

    private void rollbackLike(ApiService.CommunityPostItem item, boolean wasLiked, int oldCount) {
        item.setLiked(wasLiked);
        item.setLikeCount(oldCount);
        notifyPostChanged(item);
    }

    private void notifyPostChanged(ApiService.CommunityPostItem item) {
        int index = posts.indexOf(item);
        if (index >= 0) {
            adapter.notifyItemChanged(index);
        }
    }

    @Override
    public void onCommentClick(ApiService.CommunityPostItem item) {
        Intent intent = new Intent(requireContext(), PostCommentsActivity.class);
        intent.putExtra("postId", item.getId());
        intent.putExtra("authorName", item.getUserName());
        commentsLauncher.launch(intent);
    }

    @Override
    public void onMoreClick(ApiService.CommunityPostItem item, int position) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_post_actions, null);
        content.findViewById(R.id.ll_action_edit).setOnClickListener(v -> {
            dialog.dismiss();
            openEdit(item);
        });
        content.findViewById(R.id.ll_action_visibility).setOnClickListener(v -> {
            dialog.dismiss();
            chooseVisibility(item);
        });
        content.findViewById(R.id.ll_action_delete).setOnClickListener(v -> {
            dialog.dismiss();
            confirmDelete(item);
        });
        dialog.setContentView(content);
        dialog.show();
    }

    /** 进入编辑页：带上原文、当前可见范围与配图数量 */
    private void openEdit(ApiService.CommunityPostItem item) {
        Intent intent = new Intent(requireContext(), PublishPostActivity.class);
        intent.putExtra(PublishPostActivity.EXTRA_POST_ID, item.getId());
        intent.putExtra(PublishPostActivity.EXTRA_CONTENT, item.getContent());
        intent.putExtra(PublishPostActivity.EXTRA_VISIBILITY, item.getVisibility());
        intent.putExtra(PublishPostActivity.EXTRA_IMAGE_COUNT, item.getImageCount());
        intent.putExtra(PublishPostActivity.EXTRA_IMAGE_VERSION, item.getUpdatedAt());
        publishLauncher.launch(intent);
    }

    private void chooseVisibility(ApiService.CommunityPostItem item) {
        String[] labels = {"公开（所有人可见）", "仅自己可见"};
        int checked = item.isPrivate() ? 1 : 0;
        new AlertDialog.Builder(requireContext())
                .setTitle("谁可以看")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    int target = which == 1
                            ? ApiService.CommunityPostItem.VISIBILITY_PRIVATE
                            : ApiService.CommunityPostItem.VISIBILITY_PUBLIC;
                    if (target != item.getVisibility()) {
                        updateVisibility(item, target);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateVisibility(ApiService.CommunityPostItem item, int visibility) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", item.getContent());
        payload.put("visibility", visibility);
        ApiClient.getInstance(requireContext()).getApiService()
                .updateCommunityPost(item.getId(), payload)
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call,
                                           Response<ApiService.Response<Void>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            item.setVisibility(visibility);
                            notifyPostChanged(item);
                            ToastUtil.showSuccess(requireContext(),
                                    visibility == ApiService.CommunityPostItem.VISIBILITY_PRIVATE
                                            ? "已设为仅自己可见" : "已公开");
                        } else {
                            String message = response.body() == null
                                    ? "设置失败" : response.body().getMessage();
                            ToastUtil.showError(requireContext(), message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        LogUtil.e(TAG, "修改可见范围失败", t);
                        ToastUtil.showError(requireContext(), "网络异常，请稍后重试");
                    }
                });
    }

    private void confirmDelete(ApiService.CommunityPostItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除动态")
                .setMessage("删除后点赞和评论也会一起清除，确定删除吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deletePost(item))
                .show();
    }

    private void deletePost(ApiService.CommunityPostItem item) {
        ApiClient.getInstance(requireContext()).getApiService().deleteCommunityPost(item.getId())
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call,
                                           Response<ApiService.Response<Void>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            posts.remove(item);
                            adapter.notifyDataSetChanged();
                            updateEmptyState();
                            ToastUtil.showSuccess(requireContext(), "已删除");
                        } else {
                            String message = response.body() == null ? "删除失败" : response.body().getMessage();
                            ToastUtil.showError(requireContext(), message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        LogUtil.e(TAG, "删除动态失败", t);
                        ToastUtil.showError(requireContext(), "网络异常，请稍后重试");
                    }
                });
    }
}
