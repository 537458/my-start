package com.petshop.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.petshop.R;
import com.petshop.activity.LoginActivity;
import com.petshop.activity.MainActivity;
import com.petshop.activity.OrderActivity;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.google.gson.Gson;
import android.content.Intent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.petshop.util.GlideUtil;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CartFragment extends Fragment {

    private RecyclerView rvCartItems;
    private TextView tvTotalPrice;
    private TextView tvCheckout;
    private CheckBox cbSelectAll;
    private LinearLayout llEmptyCart;
    private Button btnGoShopping;
    private LinearLayout llCheckout;
    private LinearLayout llSelectAll;
    private TextView tvCartCount;
    private TextView tvSelectedHint;
    private View toolbarCart;
    private double totalPrice = 0;
    private List<ApiService.CartItemSimple> cartItems = new ArrayList<>();
    private CartAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cart, container, false);
        initView(view);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 检查用户是否已登录
        if (!ApiClient.getInstance(requireContext()).isLoggedIn()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(getContext(), LoginActivity.class));
            return;
        }
        
        initCart();
        loadCartFromApi();
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // 检查用户是否已登录
        if (!ApiClient.getInstance(requireContext()).isLoggedIn()) {
            return;
        }

        loadCartFromApi();
    }

    /**
     * 主页用 hide/show 切换 Fragment，切回来时不会走 onResume。
     * 不在这里主动拉取的话，刚加购的商品要等下次冷启动才会出现在购物车。
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        if (!hidden && isAdded() && ApiClient.getInstance(requireContext()).isLoggedIn()) {
            loadCartFromApi();
        }
    }

    private void initView(View view) {
        rvCartItems = view.findViewById(R.id.rv_cart_items);
        cbSelectAll = view.findViewById(R.id.cb_select_all);
        tvTotalPrice = view.findViewById(R.id.tv_total_price);
        tvCheckout = view.findViewById(R.id.btn_checkout);
        llEmptyCart = view.findViewById(R.id.ll_empty_cart);
        btnGoShopping = view.findViewById(R.id.btn_go_shopping);
        llCheckout = view.findViewById(R.id.ll_checkout);
        llSelectAll = view.findViewById(R.id.ll_select_all);
        tvCartCount = view.findViewById(R.id.tv_cart_count);
        tvSelectedHint = view.findViewById(R.id.tv_selected_hint);
        toolbarCart = view.findViewById(R.id.toolbar_cart);
        
        // 去逛逛按钮点击事件
        btnGoShopping.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 直接切换宿主页面的底部导航，避免重复创建 MainActivity 实例
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).switchToHome();
                }
            }
        });

        // 点「全选」文字也能切换，不必精确点中那个小方框
        llSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cbSelectAll.setChecked(!cbSelectAll.isChecked());
            }
        });
    }

    private void initCart() {
        rvCartItems.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CartAdapter();
        rvCartItems.setAdapter(adapter);
        
        // 添加滑动删除功能
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false; // 不支持拖动排序
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position >= 0 && position < cartItems.size()) {
                    ApiService.CartItemSimple item = cartItems.get(position);
                    // 显示撤销提示
                    showUndoDeleteSnackbar(item, position);
                } else {
                    // 位置无效时列表会停在“已滑开”状态，必须回滚刷新
                    adapter.notifyDataSetChanged();
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(rvCartItems);

        cbSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isChecked = cbSelectAll.isChecked();
                for (ApiService.CartItemSimple item : cartItems) {
                    item.setSelected(isChecked);
                }
                adapter.notifyDataSetChanged();
                updateTotalPrice();
            }
        });

        tvCheckout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<ApiService.CartItemSimple> selectedItems = new ArrayList<>();
                for (ApiService.CartItemSimple item : cartItems) {
                    if (item.isSelected()) {
                        selectedItems.add(item);
                    }
                }
                if (selectedItems.isEmpty()) {
                    Toast.makeText(getContext(), "请选择要结算的商品", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 只把勾选的商品传入订单页，未勾选的商品不参与下单
                Intent intent = new Intent(getContext(), OrderActivity.class);
                intent.putExtra("cart_items_json", new Gson().toJson(selectedItems));
                intent.putExtra("total_price", totalPrice);
                startActivity(intent);
            }
        });
    }

    private void loadCartFromApi() {
        ApiClient.getInstance(requireContext()).getApiService().getCart()
                .enqueue(new Callback<ApiService.Response<List<ApiService.CartItemSimple>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.CartItemSimple>>> call,
                                           Response<ApiService.Response<List<ApiService.CartItemSimple>>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            List<ApiService.CartItemSimple> items = response.body().getData();
                            if (items != null) {
                                // 记住刷新前哪些商品处于勾选状态，避免每次 onResume 都把用户的选择重置成全选
                                Set<Integer> selectedIds = new HashSet<>();
                                for (ApiService.CartItemSimple old : cartItems) {
                                    if (old.isSelected()) {
                                        selectedIds.add(old.getId());
                                    }
                                }
                                boolean firstLoad = cartItems.isEmpty();

                                cartItems.clear();
                                for (ApiService.CartItemSimple item : items) {
                                    // 首次加载默认全选；之后保持用户上一次的勾选结果
                                    item.setSelected(firstLoad || selectedIds.contains(item.getId()));
                                    cartItems.add(item);
                                }
                                adapter.notifyDataSetChanged();
                                updateTotalPrice();
                            }
                        }
                        // 加载完成（无论有没有商品）都要刷新一次空状态，
                        // 否则网络失败时页面会停在「有列表但没数据」的空白态
                        updateCartEmptyState();
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.CartItemSimple>>> call, Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(getContext(), "加载购物车失败", Toast.LENGTH_SHORT).show();
                        updateCartEmptyState();
                    }
                });
    }

    private void updateTotalPrice() {
        totalPrice = 0;
        int selectedCount = 0;
        for (ApiService.CartItemSimple item : cartItems) {
            if (item.isSelected()) {
                selectedCount++;
                totalPrice += item.getPrice() * item.getQuantity();
            }
        }
        tvTotalPrice.setText("¥" + String.format("%.2f", totalPrice));
        tvCheckout.setText("结算(" + selectedCount + ")");
        if (tvSelectedHint != null) {
            tvSelectedHint.setText("已选 " + selectedCount + " 件");
        }
        cbSelectAll.setChecked(selectedCount == cartItems.size() && cartItems.size() > 0);
        if (tvCartCount != null) {
            tvCartCount.setText("共 " + cartItems.size() + " 件商品");
            tvCartCount.setVisibility(cartItems.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
    
    /**
     * 更新购物车为空时的状态显示
     */
    private void updateCartEmptyState() {
        if (cartItems.isEmpty()) {
            // 空购物车：整条工具条一起隐藏，只留标题栏和空状态引导，画面更干净
            llEmptyCart.setVisibility(View.VISIBLE);
            rvCartItems.setVisibility(View.GONE);
            llCheckout.setVisibility(View.GONE);
            toolbarCart.setVisibility(View.GONE);
        } else {
            // 购物车不为空，显示工具条、商品列表和结算栏
            llEmptyCart.setVisibility(View.GONE);
            rvCartItems.setVisibility(View.VISIBLE);
            llCheckout.setVisibility(View.VISIBLE);
            toolbarCart.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 显示撤销删除的Snackbar
     */
    private void showUndoDeleteSnackbar(ApiService.CartItemSimple item, int position) {
        // 临时保存被删除的商品
        final ApiService.CartItemSimple deletedItem = item;
        final int deletedPosition = position;
        
        // 从列表中移除
        cartItems.remove(deletedPosition);
        adapter.notifyItemRemoved(deletedPosition);
        updateTotalPrice();
        updateCartEmptyState();
        
        // 异步删除后端数据；失败时会把商品放回列表
        deleteCartItem(deletedItem, deletedPosition);
        
        // 显示Snackbar提供撤销选项
        com.google.android.material.snackbar.Snackbar snackbar = 
            com.google.android.material.snackbar.Snackbar.make(
                requireView(), 
                "已删除 " + deletedItem.getProductName(), 
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            );
        
        snackbar.setAction("撤销", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreDeletedItem(deletedItem, deletedPosition);
            }
        });
        
        snackbar.show();
    }

    /**
     * 撤销删除：把商品重新写回后端购物车，并使用后端返回的新 id 更新本地数据。
     * 只恢复界面不恢复后端会导致刷新后商品彻底丢失。
     */
    private void restoreDeletedItem(ApiService.CartItemSimple deletedItem, int position) {
        if (cartItems.contains(deletedItem)) {
            return; // 删除失败时已自动回填，不必重复恢复
        }
        com.petshop.model.ShoppingCart cart = new com.petshop.model.ShoppingCart();
        cart.setProductId(deletedItem.getProductId());
        cart.setQuantity(deletedItem.getQuantity());
        ApiClient.getInstance(requireContext()).getApiService().addToCart(cart)
                .enqueue(new Callback<ApiService.Response<com.petshop.model.ShoppingCart>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<com.petshop.model.ShoppingCart>> call,
                                           Response<ApiService.Response<com.petshop.model.ShoppingCart>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200
                                && response.body().getData() != null) {
                            deletedItem.setId(response.body().getData().getId());
                            deletedItem.setSelected(true);
                        } else {
                            if (isAdded()) {
                                Toast.makeText(getContext(), "撤销失败，请重新添加商品", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        int insertIndex = Math.min(Math.max(position, 0), cartItems.size());
                        cartItems.add(insertIndex, deletedItem);
                        adapter.notifyItemInserted(insertIndex);
                        updateTotalPrice();
                        updateCartEmptyState();
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<com.petshop.model.ShoppingCart>> call, Throwable t) {
                        Toast.makeText(getContext(), "撤销失败，请重新添加商品", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    /**
     * 把商品重新插回列表（删除失败时使用）。
     * 先按 id 去重，避免「撤销」与「删除失败」同时到达时出现重复条目。
     */
    private void restoreLocally(ApiService.CartItemSimple item, int position) {
        if (adapter == null) {
            return;
        }
        for (ApiService.CartItemSimple exist : cartItems) {
            if (exist.getId() == item.getId()) {
                return; // 已经在列表里了
            }
        }
        int index = Math.min(Math.max(position, 0), cartItems.size());
        item.setSelected(true);
        cartItems.add(index, item);
        adapter.notifyItemInserted(index);
        updateTotalPrice();
        updateCartEmptyState();
    }

    /**
     * 更新购物车商品数量。
     * 服务端可能因库存不足/商品下架拒绝修改，此时必须回滚到修改前的数量，
     * 否则界面会显示一个服务端并不存在的数量。
     */
    private void updateCartItemQuantity(final ApiService.CartItemSimple item, final int oldQuantity, final int newQuantity) {
        if (oldQuantity == newQuantity) {
            return;
        }
        com.petshop.model.ShoppingCart cart = new com.petshop.model.ShoppingCart();
        cart.setQuantity(newQuantity);
        ApiClient.getInstance(requireContext()).getApiService().updateCartItem(item.getId(), cart)
                .enqueue(new Callback<ApiService.Response<com.petshop.model.ShoppingCart>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<com.petshop.model.ShoppingCart>> call, Response<ApiService.Response<com.petshop.model.ShoppingCart>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            return; // 服务端已接受，界面数量即为准
                        }
                        // 服务端拒绝：回滚到修改前数量
                        item.setQuantity(oldQuantity);
                        adapter.notifyDataSetChanged();
                        updateTotalPrice();
                        String message = response.body() != null && response.body().getMessage() != null
                                ? response.body().getMessage() : "修改数量失败";
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<com.petshop.model.ShoppingCart>> call, Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        // 网络异常：同样回滚，避免前后端数量不一致
                        item.setQuantity(oldQuantity);
                        adapter.notifyDataSetChanged();
                        updateTotalPrice();
                        Toast.makeText(getContext(), "网络异常，数量未保存", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    /**
     * 删除购物车商品；服务端删除失败时把商品放回列表，防止刷新后又“复活”
     */
    private void deleteCartItem(final ApiService.CartItemSimple item, final int position) {
        ApiClient.getInstance(requireContext()).getApiService().deleteCartItem(item.getId())
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call, Response<ApiService.Response<Void>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            return;
                        }
                        restoreLocally(item, position);
                        String message = response.body() != null && response.body().getMessage() != null
                                ? response.body().getMessage() : "删除失败";
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        restoreLocally(item, position);
                        Toast.makeText(getContext(), "网络异常，删除失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

        @NonNull
        @Override
        public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
            return new CartViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
            ApiService.CartItemSimple item = cartItems.get(position);
            holder.cbSelect.setChecked(item.isSelected());
            holder.tvProductName.setText(item.getProductName());
            holder.tvProductPrice.setText("¥" + String.format("%.2f", item.getPrice()));
            holder.tvQuantity.setText(String.valueOf(item.getQuantity()));

            // 库存紧张时给个提示，缺货用红色，让用户知道为什么加不上去
            if (item.getStock() <= 0) {
                holder.tvStock.setVisibility(View.VISIBLE);
                holder.tvStock.setText("暂时缺货");
                holder.tvStock.setTextColor(holder.itemView.getContext().getColor(R.color.colorDanger));
            } else if (item.getStock() <= 5) {
                holder.tvStock.setVisibility(View.VISIBLE);
                holder.tvStock.setText("仅剩 " + item.getStock() + " 件");
                holder.tvStock.setTextColor(holder.itemView.getContext().getColor(R.color.orange));
            } else {
                holder.tvStock.setVisibility(View.GONE);
            }
            
            // 使用优化后的 GlideUtil 加载商品图片，避免 Blob 数据导致的卡顿
            // 优先使用 item 中的 imageUrl，没有的话再用商品ID构建URL
            String imageUrl = item.getImageUrl();
            String baseUrl = com.petshop.api.ApiClient.getBaseUrl();
            
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = baseUrl + "images/product/" + item.getProductId();
            } else if (imageUrl.startsWith("http") && imageUrl.contains("/uploads/")) {
                // 如果是HTTP URL指向上传目录，改用后端BLOB接口
                imageUrl = baseUrl + "images/product/" + item.getProductId();
            } else if (!imageUrl.startsWith("data:") && !imageUrl.startsWith("http")) {
                // 相对路径拼接基础URL
                if (imageUrl.startsWith("/")) {
                    imageUrl = baseUrl + imageUrl.substring(1);
                } else {
                    imageUrl = baseUrl + imageUrl;
                }
            }
            GlideUtil.loadThumbnail(getContext(), imageUrl, holder.ivProduct);

            holder.cbSelect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    item.setSelected(holder.cbSelect.isChecked());
                    updateTotalPrice();
                }
            });

            holder.tvMinus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int quantity = item.getQuantity();
                    if (quantity > 1) {
                        item.setQuantity(quantity - 1);
                        holder.tvQuantity.setText(String.valueOf(item.getQuantity()));
                        updateTotalPrice();
                        // 同步到后端，失败时回滚到 quantity
                        updateCartItemQuantity(item, quantity, item.getQuantity());
                    }
                }
            });

            holder.tvPlus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int quantity = item.getQuantity();
                    // 加购数量不能超过当前库存，避免下单时才报库存不足
                    if (item.getStock() > 0 && quantity >= item.getStock()) {
                        Toast.makeText(getContext(), "库存仅剩 " + item.getStock() + " 件", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    item.setQuantity(quantity + 1);
                    holder.tvQuantity.setText(String.valueOf(item.getQuantity()));
                    updateTotalPrice();
                    // 同步到后端，失败时回滚到 quantity
                    updateCartItemQuantity(item, quantity, item.getQuantity());
                }
            });

            holder.tvDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // position 是绑定时的旧值，列表变动后可能已失效，必须实时取
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION || pos >= cartItems.size()) {
                        return;
                    }
                    ApiService.CartItemSimple target = cartItems.get(pos);
                    cartItems.remove(pos);
                    adapter.notifyItemRemoved(pos);
                    updateTotalPrice();
                    updateCartEmptyState();
                    // 同步到后端，失败时会把商品放回原位
                    deleteCartItem(target, pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return cartItems.size();
        }

        class CartViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbSelect;
            ImageView ivProduct;
            TextView tvProductName;
            TextView tvProductPrice;
            TextView tvStock;
            ImageView tvMinus;
            TextView tvQuantity;
            ImageView tvPlus;
            ImageView tvDelete;

            public CartViewHolder(@NonNull View itemView) {
                super(itemView);
                cbSelect = itemView.findViewById(R.id.cb_select);
                ivProduct = itemView.findViewById(R.id.iv_product);
                tvProductName = itemView.findViewById(R.id.tv_product_name);
                tvProductPrice = itemView.findViewById(R.id.tv_product_price);
                tvStock = itemView.findViewById(R.id.tv_stock);
                tvMinus = itemView.findViewById(R.id.tv_minus);
                tvQuantity = itemView.findViewById(R.id.tv_quantity);
                tvPlus = itemView.findViewById(R.id.tv_plus);
                tvDelete = itemView.findViewById(R.id.tv_delete);
            }
        }
    }
}