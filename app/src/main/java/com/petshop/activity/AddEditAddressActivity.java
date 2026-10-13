package com.petshop.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;

import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.model.Address;
import com.petshop.util.AuthUtil;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddEditAddressActivity extends AppCompatActivity {

    private EditText etName;
    private EditText etPhone;
    private EditText etProvince;
    private EditText etCity;
    private EditText etDistrict;
    private EditText etDetailAddress;
    private RadioButton rbDefault;
    private TextView btnSave;
    private TextView btnBack;

    private int addressId = -1; // -1表示添加新地址，否则表示编辑已有地址

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_address);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        initView();
        initData();
        setListeners();
    }

    private void initView() {
        etName = findViewById(R.id.et_name);
        etPhone = findViewById(R.id.et_phone);
        etProvince = findViewById(R.id.et_province);
        etCity = findViewById(R.id.et_city);
        etDistrict = findViewById(R.id.et_district);
        etDetailAddress = findViewById(R.id.et_detail_address);
        rbDefault = findViewById(R.id.rb_default);
        btnSave = findViewById(R.id.btn_save);
        btnBack = findViewById(R.id.btn_back);
    }

    private void initData() {
        // 获取传入的地址信息（如果是编辑模式）
        Intent intent = getIntent();
        addressId = intent.getIntExtra("address_id", -1);
        String name = intent.getStringExtra("name");
        String phone = intent.getStringExtra("phone");
        String province = intent.getStringExtra("province");
        String city = intent.getStringExtra("city");
        String district = intent.getStringExtra("district");
        String detailAddress = intent.getStringExtra("detailAddress");
        boolean isDefault = intent.getBooleanExtra("is_default", false);

        // 如果是编辑模式，填充已有信息
        if (addressId != -1) {
            etName.setText(name);
            etPhone.setText(phone);
            etProvince.setText(province);
            etCity.setText(city);
            etDistrict.setText(district);
            etDetailAddress.setText(detailAddress);
            rbDefault.setChecked(isDefault);
        } else {
            // 新增地址时设置默认值
            etProvince.setText("江西省");
            etCity.setText("南昌市");
            etDistrict.setText("新建区");
            etDetailAddress.setText("华东交通大学");
        }
    }

    private void setListeners() {
        // 返回按钮点击事件
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 保存按钮点击事件
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 获取输入信息
                String name = etName.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String province = etProvince.getText().toString().trim();
                String city = etCity.getText().toString().trim();
                String district = etDistrict.getText().toString().trim();
                String detailAddress = etDetailAddress.getText().toString().trim();
                boolean isDefault = rbDefault.isChecked();

                // 验证输入
                if (name.isEmpty() || phone.isEmpty() || province.isEmpty() || city.isEmpty() || district.isEmpty() || detailAddress.isEmpty()) {
                    Toast.makeText(AddEditAddressActivity.this, "请填写完整的地址信息哦", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 验证手机号格式
                if (!isValidPhone(phone)) {
                    Toast.makeText(AddEditAddressActivity.this, "请输入正确的手机号", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 创建Address对象
                Address addressObj = new Address();
                addressObj.setName(name);
                addressObj.setPhone(phone);
                addressObj.setProvince(province);
                addressObj.setCity(city);
                addressObj.setDistrict(district);
                addressObj.setDetailAddress(detailAddress);
                addressObj.setDefault(isDefault);

                // 调用后端API保存地址
                ApiService apiService = ApiClient.getInstance(AddEditAddressActivity.this).getApiService();
                Call<ApiService.Response<Address>> call;

                if (addressId == -1) {
                    // 添加新地址
                    call = apiService.createAddress(addressObj);
                } else {
                    // 编辑已有地址
                    call = apiService.updateAddress(addressId, addressObj);
                }

                call.enqueue(new Callback<ApiService.Response<Address>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Address>> call, Response<ApiService.Response<Address>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            // 检查响应码是否为200
                            if (response.body().getCode() == 200) {
                                // 保存成功
                                Toast.makeText(AddEditAddressActivity.this, "地址保存成功", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent();
                                setResult(RESULT_OK, intent);
                                finish();
                            } else {
                                // 保存失败
                                String message = response.body().getMessage() != null ? response.body().getMessage() : "地址保存失败";
                                Toast.makeText(AddEditAddressActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // 保存失败
                            Toast.makeText(AddEditAddressActivity.this, "地址保存失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Address>> call, Throwable t) {
                        // 网络错误
                        Toast.makeText(AddEditAddressActivity.this, "网络连接失败，请稍后重试", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // 验证手机号格式
    private boolean isValidPhone(String phone) {
        return phone.matches("1[3-9]\\d{9}");
    }
}