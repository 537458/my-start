package com.petshop.activity;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.petshop.R;
import com.petshop.adapter.ProductAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.model.Product;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchResultActivity extends AppCompatActivity {

    private ImageView ivBack;
    private TextView tvKeyword;
    private RecyclerView rvProducts;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout llEmpty, llError, llSort, llFilter;
    private TextView tvSortDefault, tvSortSales, tvSortPrice, tvError;
    private Button btnRetry;
    private ProductAdapter productAdapter;
    private List<Product> productList;
    private String keyword;
    private int page = 1;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private String sortBy = "default"; // default, sales, price

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_result);
        initView();
        initData();
        initListeners();
        loadProducts();
    }

    private void initView() {
        ivBack = findViewById(R.id.iv_back);
        tvKeyword = findViewById(R.id.tv_keyword);
        rvProducts = findViewById(R.id.rv_products);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        llEmpty = findViewById(R.id.ll_empty);
        llError = findViewById(R.id.ll_error);
        llSort = findViewById(R.id.ll_sort);
        llFilter = findViewById(R.id.ll_filter);
        tvSortDefault = findViewById(R.id.tv_sort_default);
        tvSortSales = findViewById(R.id.tv_sort_sales);
        tvSortPrice = findViewById(R.id.tv_sort_price);
        tvError = findViewById(R.id.tv_error);
        btnRetry = findViewById(R.id.btn_retry);
        
        // 设置下拉刷新
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::loadProducts);
            swipeRefresh.setColorSchemeResources(
                R.color.colorPrimary,
                R.color.colorAccent,
                R.color.colorPrimaryDark
            );
        }
    }

    private void initData() {
        keyword = getIntent().getStringExtra("keyword");
        if (keyword == null) {
            keyword = "";
        }
        tvKeyword.setText("搜索: " + keyword);

        productList = new ArrayList<>();
        productAdapter = new ProductAdapter(this, productList);

        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        rvProducts.setLayoutManager(layoutManager);
        rvProducts.setAdapter(productAdapter);

        // 上拉加载
        rvProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) {
                    GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                        int totalItemCount = layoutManager.getItemCount();
                        if (lastVisibleItem >= totalItemCount - 2 && !isLoading && hasMore) {
                            loadMoreProducts();
                        }
                    }
                }
            }
        });
    }

    private void initListeners() {
        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 排序按钮点击事件
        tvSortDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSortBy("default");
            }
        });

        tvSortSales.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSortBy("sales");
            }
        });

        tvSortPrice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSortBy("price");
            }
        });

        // 筛选按钮点击事件
        llFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 这里可以实现筛选功能
                Toast.makeText(SearchResultActivity.this, "筛选功能开发中", Toast.LENGTH_SHORT).show();
            }
        });

        // 重试按钮点击事件
        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadProducts();
            }
        });
    }

    private void setSortBy(String sort) {
        sortBy = sort;
        resetUI();
        refreshProducts();
    }

    private void resetUI() {
        tvSortDefault.setTextColor(sortBy.equals("default") ? getResources().getColor(R.color.orange) : getResources().getColor(android.R.color.darker_gray));
        tvSortSales.setTextColor(sortBy.equals("sales") ? getResources().getColor(R.color.orange) : getResources().getColor(android.R.color.darker_gray));
        tvSortPrice.setTextColor(sortBy.equals("price") ? getResources().getColor(R.color.orange) : getResources().getColor(android.R.color.darker_gray));
    }

    private void loadProducts() {
        if (isLoading) return;

        // 检查网络连接
        if (!isNetworkConnected()) {
            showError("网络连接失败，请检查网络设置");
            return;
        }

        isLoading = true;
        showLoading();

        try {
            ApiService apiService = ApiClient.getInstance(this).getApiService();
            Call<ApiService.Response<List<Product>>> call = apiService.searchProducts(keyword, sortBy, page, 10);

            call.enqueue(new Callback<ApiService.Response<List<Product>>>() {
                @Override
                public void onResponse(Call<ApiService.Response<List<Product>>> call, Response<ApiService.Response<List<Product>>> response) {
                    isLoading = false;
                    hideLoading();

                    // 添加日志
                    android.util.Log.d("SearchResult", "Response code: " + response.code());
                    android.util.Log.d("SearchResult", "Response message: " + response.message());
                    if (response.body() != null) {
                        android.util.Log.d("SearchResult", "Response body code: " + response.body().getCode());
                        android.util.Log.d("SearchResult", "Response body message: " + response.body().getMessage());
                        android.util.Log.d("SearchResult", "Response body data: " + response.body().getData());
                    } else {
                        android.util.Log.d("SearchResult", "Response body is null");
                    }

                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            if (response.body().getData() != null) {
                                List<Product> products = response.body().getData();
                                if (page == 1) {
                                    productAdapter.updateData(products);
                                    productList.clear();
                                    productList.addAll(products);
                                } else {
                                    productList.addAll(products);
                                    productAdapter.notifyDataSetChanged();
                                }
                                hasMore = products.size() >= 10;
                                updateEmptyState();
                            } else {
                                showError("没有找到相关商品");
                            }
                        } else {
                            showError("搜索失败，请重试");
                        }
                    } catch (Exception e) {
                        com.petshop.util.LogUtil.e("SearchResult", "解析数据失败", e);
                        showError("数据解析失败，请重试");
                    }
                }

                @Override
                public void onFailure(Call<ApiService.Response<List<Product>>> call, Throwable t) {
                    isLoading = false;
                    hideLoading();
                    // 添加日志
                    android.util.Log.e("SearchResult", "网络请求失败", t);
                    showError("网络错误，请检查网络连接");
                }
            });
        } catch (Exception e) {
            isLoading = false;
            hideLoading();
            android.util.Log.e("SearchResult", "初始化请求失败", e);
            showError("请求失败，请重试");
        }
    }

    private void loadMoreProducts() {
        if (hasMore) {
            page++;
            loadProducts();
        }
    }

    private void refreshProducts() {
        page = 1;
        hasMore = true;
        loadProducts();
    }

    private void updateEmptyState() {
        if (productList.isEmpty()) {
            llEmpty.setVisibility(View.VISIBLE);
            rvProducts.setVisibility(View.GONE);
            llError.setVisibility(View.GONE);
        } else {
            llEmpty.setVisibility(View.GONE);
            rvProducts.setVisibility(View.VISIBLE);
            llError.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        llError.setVisibility(View.VISIBLE);
        rvProducts.setVisibility(View.GONE);
        llEmpty.setVisibility(View.GONE);
        tvError.setText(message);
    }

    private void showLoading() {
        // 显示加载动画
        if (swipeRefresh != null && !swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(true);
        }
    }

    private void hideLoading() {
        // 隐藏加载动画
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(false);
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
}
