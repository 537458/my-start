 package com.petshop.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.petshop.R;
import com.petshop.activity.MainActivity;
import com.petshop.activity.SearchActivity;
import com.petshop.adapter.ProductAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.api.NetworkCallback;
import com.petshop.model.Product;
import com.petshop.util.LogUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private ViewPager2 vpBanner;
    private LinearLayout llBannerIndicator;
    private int[] bannerImages = {
        R.drawable.banner_cat,
        R.drawable.banner_cat1,
        R.drawable.banner_dog,
        R.drawable.banner_pet,
        R.drawable.banner_pet4,
        R.drawable.banner_pet5,
        R.drawable.banner_pet6,

    };
    private int currentBannerPosition = 0;
    private Handler handler;
    private Runnable bannerRunnable;

    private RecyclerView rvHotProducts;
    private ProductAdapter productAdapter;
    private List<Product> productList;
    private View searchLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        android.util.Log.d("HomeFragment", "=== onCreateView START ===");
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        android.util.Log.d("HomeFragment", "View inflated");
        try {
            initView(view);
            android.util.Log.d("HomeFragment", "initView done");
            initBanner();
            android.util.Log.d("HomeFragment", "initBanner done");
            initCategoryNavigation(view);
            android.util.Log.d("HomeFragment", "initCategoryNavigation done");
        } catch (Exception e) {
            android.util.Log.e("HomeFragment", "Error in onCreateView: " + e.getMessage(), e);
        }
        android.util.Log.d("HomeFragment", "=== onCreateView END ===");
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        android.util.Log.d("HomeFragment", "=== onViewCreated START ===");
        try {
            initHotProducts();
            android.util.Log.d("HomeFragment", "Products: " + productAdapter.getItemCount());
            
            loadProducts();
            android.util.Log.d("HomeFragment", "=== onViewCreated END ===");
        } catch (Exception e) {
            android.util.Log.e("HomeFragment", "Error in onViewCreated: " + e.getMessage(), e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initView(View view) {
        vpBanner = view.findViewById(R.id.vp_banner);
        llBannerIndicator = view.findViewById(R.id.ll_banner_indicator);
        rvHotProducts = view.findViewById(R.id.rv_hot_products);
        searchLayout = view.findViewById(R.id.rl_search);
        
        searchLayout.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
        });
    }

    private void initBanner() {
        vpBanner.setAdapter(new BannerAdapter(bannerImages));
        vpBanner.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);

        for (int i = 0; i < bannerImages.length; i++) {
            ImageView indicator = new ImageView(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    8, 8);
            params.setMargins(4, 0, 4, 0);
            indicator.setLayoutParams(params);
            indicator.setImageResource(i == 0 ? R.drawable.shape_indicator_selected : R.drawable.shape_indicator_normal);
            llBannerIndicator.addView(indicator);
        }

        vpBanner.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentBannerPosition = position;
                updateBannerIndicator();
            }
        });

        startAutoBanner();
    }

    private void startAutoBanner() {
        stopAutoBanner(); // 先清除之前的任务，防止重复启动导致跳过多张
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        
        bannerRunnable = new BannerRunnable(this);
        handler.postDelayed(bannerRunnable, 3000);//初始启动延迟3秒，单位为毫秒
    }

    private void stopAutoBanner() {
        if (handler != null && bannerRunnable != null) {
            handler.removeCallbacks(bannerRunnable);
            bannerRunnable = null;
        }
    }

    private void updateBannerIndicator() {
        for (int i = 0; i < llBannerIndicator.getChildCount(); i++) {
            ImageView indicator = (ImageView) llBannerIndicator.getChildAt(i);
            indicator.setImageResource(i == currentBannerPosition ? R.drawable.shape_indicator_selected : R.drawable.shape_indicator_normal);
        }
    }

    private void switchToNextBanner() {
        if (vpBanner != null && isAdded()) {
            currentBannerPosition = (currentBannerPosition + 1) % bannerImages.length;
            vpBanner.setCurrentItem(currentBannerPosition, true);
        }
    }

    private void initCategoryNavigation(View view) {
        if (view == null) return;
        
        view.findViewById(R.id.iv_category_food).setOnClickListener(v -> switchToCategory(0));
        view.findViewById(R.id.iv_category_toy).setOnClickListener(v -> switchToCategory(1));
        view.findViewById(R.id.iv_category_house).setOnClickListener(v -> switchToCategory(2));
        view.findViewById(R.id.iv_category_clean).setOnClickListener(v -> switchToCategory(3));
        view.findViewById(R.id.iv_category_medical).setOnClickListener(v -> switchToCategory(4));
        view.findViewById(R.id.iv_category_clothing).setOnClickListener(v -> switchToCategory(5));
        view.findViewById(R.id.iv_category_service).setOnClickListener(v -> switchToCategory(6));
        view.findViewById(R.id.iv_category_more).setOnClickListener(v -> switchToCategory(7));
    }
    
    private void switchToCategory(int categoryIndex) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            mainActivity.switchToCategoryFragment(categoryIndex);
        }
    }

    private void initHotProducts() {
        if (getContext() == null || rvHotProducts == null) {
            android.util.Log.e("HomeFragment", "Cannot init products: context or rvHotProducts is null");
            return;
        }
        
        productList = new ArrayList<>();
        productAdapter = new ProductAdapter(getContext(), productList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        rvHotProducts.setLayoutManager(layoutManager);
        rvHotProducts.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        rvHotProducts.setHasFixedSize(true);
        rvHotProducts.setAdapter(productAdapter);
    }

    private void loadProducts() {
        ApiClient apiClient = ApiClient.getInstance(getContext());
        if (apiClient != null) {
            ApiService apiService = apiClient.getApiService();
            apiService.getProducts().enqueue(new NetworkCallback<List<Product>>(getContext()) {
                @Override
                protected void onSuccess(List<Product> products) {
                    if (products != null) {
                        productAdapter.updateData(products);
                    }
                }

                @Override
                protected void onNetworkError(String message) {
                    LogUtil.e("HomeFragment", "加载商品失败: " + message);
                }
            });
        }
    }

    

    @Override
    public void onPause() {
        super.onPause();
        stopAutoBanner();
    }

    @Override
    public void onResume() {
        super.onResume();
        startAutoBanner();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAutoBanner();
        handler = null;
    }

    private static class BannerRunnable implements Runnable {
        private final WeakReference<HomeFragment> fragmentRef;

        BannerRunnable(HomeFragment fragment) {
            this.fragmentRef = new WeakReference<>(fragment);
        }

        @Override
        public void run() {
            HomeFragment fragment = fragmentRef.get();
            if (fragment != null && fragment.isAdded()) {
                fragment.switchToNextBanner();
                if (fragment.handler != null) {
                    fragment.handler.postDelayed(this, 3000);//循环间隔延迟3秒，单位为毫秒
                }
            }
        }
    }

    private class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.BannerViewHolder> {

        private int[] images;

        public BannerAdapter(int[] images) {
            this.images = images;
        }

        @NonNull
        @Override
        public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new BannerViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
            // 使用本地资源ID加载图片
            holder.imageView.setImageResource(images[position]);
            
            holder.itemView.setOnClickListener(v -> {
                MainActivity mainActivity = (MainActivity) getActivity();
                if (mainActivity != null) {
                    mainActivity.switchToCategoryFragment(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return images.length;
        }

        class BannerViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            public BannerViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = (ImageView) itemView;
            }
        }
    }
}