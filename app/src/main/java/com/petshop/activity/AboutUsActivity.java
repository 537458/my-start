package com.petshop.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.petshop.R;

public class AboutUsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("关于我们");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        initView();
    }

    private void initView() {
        TextView tvVersion = findViewById(R.id.tv_version);
        TextView tvCompanyName = findViewById(R.id.tv_company_name);
        TextView tvCompanyAddress = findViewById(R.id.tv_company_address);
        TextView tvCompanyPhone = findViewById(R.id.tv_company_phone);
        TextView tvCompanyEmail = findViewById(R.id.tv_company_email);
        TextView tvCopyright = findViewById(R.id.tv_copyright);

        // 设置关于我们页面的内容
        tvVersion.setText("版本号：6.6.6");
        tvCompanyName.setText("公司名称：宠物乐园");
        tvCompanyAddress.setText("公司地址：江西省南昌市华东交通大学");
        tvCompanyPhone.setText("联系电话：15579161054");
        tvCompanyEmail.setText("邮箱：3321263620qq.com");
        tvCopyright.setText("© 2026 宠物乐园 版权所有");    
    }
}