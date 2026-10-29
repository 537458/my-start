package com.petshop.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.petshop.R;
import com.petshop.activity.SearchActivity;
import com.petshop.adapter.CategoryLeftAdapter;
import com.petshop.adapter.ProductAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.databinding.FragmentCategoryBinding;
import com.petshop.model.Category;
import com.petshop.model.Product;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CategoryFragment extends Fragment {

    private FragmentCategoryBinding binding;
    private CategoryLeftAdapter categoryLeftAdapter;
    private ProductAdapter productAdapter;
    private List<Category> categories = new ArrayList<>();
    private List<Product> products = new ArrayList<>();
    private int selectedCategoryId = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryBinding.inflate(inflater, container, false);
        initView();
        loadCategories();
        return binding.getRoot();
    }

    private void initView() {
        binding.searchBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getContext(), SearchActivity.class));
            }
        });

        initCategoryLeft();
        initCategoryRight();
    }

    private void initCategoryLeft() {
        binding.rvCategoryLeft.setLayoutManager(new LinearLayoutManager(getContext()));
        categoryLeftAdapter = new CategoryLeftAdapter(categories);
        binding.rvCategoryLeft.setAdapter(categoryLeftAdapter);
        categoryLeftAdapter.setOnItemClickListener(new CategoryLeftAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                if (categories != null && !categories.isEmpty() && position >= 0 && position < categories.size()) {
                    selectedCategoryId = categories.get(position).getId();
                    categoryLeftAdapter.setSelectedPosition(position);
                    loadProducts(selectedCategoryId);
                }
            }
        });
    }

    private void initCategoryRight() {
        binding.rvCategoryRight.setLayoutManager(new GridLayoutManager(getContext(), 2));
        productAdapter = new ProductAdapter(getContext(), products, true);
        binding.rvCategoryRight.setAdapter(productAdapter);
    }

    private void showLoading() {
        binding.llLoading.setVisibility(View.VISIBLE);
        binding.llContent.setVisibility(View.GONE);
        binding.llEmpty.setVisibility(View.GONE);
    }

    private void showContent() {
        binding.llLoading.setVisibility(View.GONE);
        binding.llContent.setVisibility(View.VISIBLE);
        binding.llEmpty.setVisibility(View.GONE);
    }

    private void showEmpty() {
        binding.llLoading.setVisibility(View.GONE);
        binding.llContent.setVisibility(View.GONE);
        binding.llEmpty.setVisibility(View.VISIBLE);
    }

    private void loadCategories() {
        showLoading();
        
        ApiService api = ApiClient.getInstance(getContext()).getApiService();
        Call<ApiService.Response<List<ApiService.Category>>> call = api.getCategories();
        call.enqueue(new Callback<ApiService.Response<List<ApiService.Category>>>() {
            @Override
            public void onResponse(Call<ApiService.Response<List<ApiService.Category>>> call,
                                   Response<ApiService.Response<List<ApiService.Category>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                    List<ApiService.Category> list = response.body().getData();
                    if (list != null && !list.isEmpty()) {
                        List<Category> categoryList = new ArrayList<>();
                        for (ApiService.Category c : list) {
                            Category cat = new Category();
                            cat.setId(c.getId());
                            cat.setName(c.getName());
                            categoryList.add(cat);
                        }
                        // 换引用而不是 clear/addAll：CategoryLeftAdapter 构造时拿的就是
                        // categories 这个对象，先改它等于把 DiffUtil 的“旧列表”也改成了新内容
                        categories = categoryList;
                        categoryLeftAdapter.updateData(categoryList);
                        selectedCategoryId = list.get(0).getId();
                        showContent();
                        loadProducts(selectedCategoryId);
                    } else {
                        showEmpty();
                    }
                } else {
                    showEmpty();
                    Toast.makeText(getContext(), "加载分类失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiService.Response<List<ApiService.Category>>> call, Throwable t) {
                com.petshop.util.LogUtil.e("CategoryFragment", "加载分类失败", t);
                showEmpty();
                com.petshop.util.ToastUtil.showError(getContext(), "加载分类失败：" + t.getMessage());
            }
        });
    }

    private void loadProducts(int categoryId) {
        ApiService api = ApiClient.getInstance(getContext()).getApiService();
        Call<ApiService.Response<List<Product>>> call = api.getProductsByCategory(categoryId);
        call.enqueue(new Callback<ApiService.Response<List<Product>>>() {
            @Override
            public void onResponse(Call<ApiService.Response<List<Product>>> call,
                                   Response<ApiService.Response<List<Product>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                    applyProducts(response.body().getData());
                } else {
                    applyProducts(null);
                    Toast.makeText(getContext(), "加载商品失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiService.Response<List<Product>>> call, Throwable t) {
                applyProducts(null);
                Toast.makeText(getContext(), "加载商品失败：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 刷新右侧商品区。
     * 必须先换引用再交给 updateData，不能先 products.clear()/addAll()：
     * ProductAdapter 构造时传入的就是 products 这个 List（BaseAdapter 直接持有引用），
     * 先改它会让 DiffUtil 以为新旧列表完全一致，算出零变化、一条通知都不发，
     * 而商品区的首帧布局在 showContent() 时就以 0 条完成了，
     * 表现为第一次进分类页右侧全空，切一次分类才出数据。
     */
    private void applyProducts(List<Product> list) {
        if (productAdapter == null) {
            return;
        }
        products = list != null ? list : new ArrayList<>();
        productAdapter.updateData(products);
        updateProductEmptyState(products.isEmpty());
    }

    /** 切换右侧商品区与“该分类暂无商品”提示的显示 */
    private void updateProductEmptyState(boolean empty) {
        binding.llCategoryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvCategoryRight.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
    
    /**
     * 选择指定的分类
     * @param categoryIndex 分类索引
     */
    public void selectCategory(int categoryIndex) {
        if (categories != null && !categories.isEmpty() && categoryLeftAdapter != null) {
            if (categoryIndex < 0) {
                categoryIndex = 0;
            } else if (categoryIndex >= categories.size()) {
                categoryIndex = categories.size() - 1;
            }
            selectedCategoryId = categories.get(categoryIndex).getId();
            categoryLeftAdapter.setSelectedPosition(categoryIndex);
            loadProducts(selectedCategoryId);
        }
    }
}