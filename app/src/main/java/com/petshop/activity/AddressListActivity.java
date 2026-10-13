package com.petshop.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

import com.petshop.R;
import com.petshop.adapter.AddressAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.model.Address;
import com.petshop.util.AuthUtil;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddressListActivity extends AppCompatActivity {

    private static final String TAG = "AddressListActivity";
    private RecyclerView rvAddressList;
    private TextView btnBack;
    private TextView btnAddAddress;
    private TextView tvTitle;
    private AddressAdapter addressAdapter;
    private List<Address> addressList;
    private boolean isSelectMode = false;
    private int currentAddressId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: 开始创建AddressListActivity");
        setContentView(R.layout.activity_address_list);
        Log.d(TAG, "onCreate: 布局加载完成");

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        isSelectMode = getIntent().getBooleanExtra("SELECT_MODE", false);
        currentAddressId = getIntent().getIntExtra("CURRENT_ADDRESS_ID", 0);

        initView();
        Log.d(TAG, "onCreate: initView完成");
        initData();
        Log.d(TAG, "onCreate: initData完成");
        setListeners();
        Log.d(TAG, "onCreate: setListeners完成");
    }

    private void initView() {
        Log.d(TAG, "initView: 开始初始化视图");
        rvAddressList = findViewById(R.id.rv_address_list);
        Log.d(TAG, "initView: rvAddressList=" + rvAddressList);
        btnBack = findViewById(R.id.btn_back);
        Log.d(TAG, "initView: btnBack=" + btnBack);
        btnAddAddress = findViewById(R.id.tv_add_address);
        Log.d(TAG, "initView: btnAddAddress=" + btnAddAddress);
        // 找到顶部标题栏中的TextView
        View topBar = findViewById(R.id.top_bar);
        if (topBar != null && topBar instanceof LinearLayout) {
            LinearLayout linearTopBar = (LinearLayout) topBar;
            for (int i = 0; i < linearTopBar.getChildCount(); i++) {
                View child = linearTopBar.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    // 检查是否是标题TextView（中间的那个，不是返回按钮）
                    if (tv.getId() != R.id.btn_back) {
                        tvTitle = tv;
                        break;
                    }
                }
            }
        }

        // 根据模式设置标题
        if (tvTitle != null) {
            if (isSelectMode) {
                tvTitle.setText("选择收货地址");
            } else {
                tvTitle.setText("地址管理");
            }
        }
    }

    private void initData() {
        Log.d(TAG, "initData: 开始初始化数据");
        // 初始化地址列表
        addressList = new ArrayList<>();
        Log.d(TAG, "initData: addressList初始化完成");

        // 初始化RecyclerView
        Log.d(TAG, "initData: 开始初始化AddressAdapter");
        addressAdapter = new AddressAdapter(this, addressList, new AddressAdapter.OnAddressClickListener() {
            @Override
            public void onAddressClick(Address address) {
                // 返回选中的地址
                Intent intent = new Intent();
                intent.putExtra("addressId", address.getId());
                intent.putExtra("name", address.getName());
                intent.putExtra("phone", address.getPhone());
                intent.putExtra("province", address.getProvince());
                intent.putExtra("city", address.getCity());
                intent.putExtra("district", address.getDistrict());
                intent.putExtra("detailAddress", address.getDetailAddress());
                intent.putExtra("address", address.getAddress());
                setResult(RESULT_OK, intent);
                finish();
            }

            @Override
            public void onEditClick(Address address) {
                // 跳转到编辑地址页面
                Intent intent = new Intent(AddressListActivity.this, AddEditAddressActivity.class);
                intent.putExtra("address_id", address.getId());
                intent.putExtra("name", address.getName());
                intent.putExtra("phone", address.getPhone());
                intent.putExtra("province", address.getProvince());
                intent.putExtra("city", address.getCity());
                intent.putExtra("district", address.getDistrict());
                intent.putExtra("detailAddress", address.getDetailAddress());
                intent.putExtra("is_default", address.isDefault());
                startActivityForResult(intent, 1);
            }

            @Override
            public void onDeleteClick(final Address address) {
                // 删除地址
                if (address.isDefault()) {
                    Toast.makeText(AddressListActivity.this, "默认地址不能删除哦", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 调用后端API删除地址
                ApiService apiService = ApiClient.getInstance(AddressListActivity.this).getApiService();
                Call<ApiService.Response<Void>> call = apiService.deleteAddress(address.getId());
                call.enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call, Response<ApiService.Response<Void>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                            addressList.remove(address);
                            addressAdapter.notifyDataSetChanged();
                            Toast.makeText(AddressListActivity.this, R.string.success_delete, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(AddressListActivity.this, R.string.error_operation_failed, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        Toast.makeText(AddressListActivity.this, R.string.error_network_disconnected, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        rvAddressList.setLayoutManager(new LinearLayoutManager(this));
        rvAddressList.setAdapter(addressAdapter);

        // 从后端API获取地址列表
        Log.d(TAG, "initData: 开始加载地址列表");
        loadAddresses();
        Log.d(TAG, "initData: loadAddresses调用完成");
    }

    private void loadAddresses() {
        Log.d(TAG, "loadAddresses: 开始调用API");
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        Log.d(TAG, "loadAddresses: apiService=" + apiService);
        Call<ApiService.Response<List<Address>>> call = apiService.getAddresses();
        Log.d(TAG, "loadAddresses: call=" + call);
        call.enqueue(new Callback<ApiService.Response<List<Address>>>() {
            @Override
            public void onResponse(Call<ApiService.Response<List<Address>>> call, Response<ApiService.Response<List<Address>>> response) {
                Log.d(TAG, "onResponse: 收到响应");
                if (response.isSuccessful() && response.body() != null && response.body().getCode() == 200) {
                    List<Address> addresses = response.body().getData();
                    Log.d(TAG, "onResponse: 获取到" + (addresses != null ? addresses.size() : 0) + "个地址");
                    if (addresses != null) {
                        addressList.clear();
                        addressList.addAll(addresses);
                        addressAdapter.notifyDataSetChanged();
                    }
                } else {
                    Log.e(TAG, "onResponse: 获取地址列表失败, response=" + response);
                    Toast.makeText(AddressListActivity.this, "获取地址列表失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiService.Response<List<Address>>> call, Throwable t) {
                Log.e(TAG, "onFailure: 网络错误", t);
                Toast.makeText(AddressListActivity.this, R.string.error_network_disconnected, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setListeners() {
        // 返回按钮点击事件
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 添加新地址按钮点击事件
        btnAddAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 跳转到添加地址页面
                Intent intent = new Intent(AddressListActivity.this, AddEditAddressActivity.class);
                startActivityForResult(intent, 1);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            // 重新加载地址列表
            loadAddresses();
        }
    }
}