package com.petshop.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.content.Intent;

import com.petshop.R;

public class OrderRemarkActivity extends AppCompatActivity {

    private EditText etRemark;
    private TextView btnBack;
    private TextView btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_remark);

        initView();
        initData();
        setListeners();
    }

    private void initView() {
        etRemark = findViewById(R.id.et_remark);
        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save);
    }

    private void initData() {
        // 获取传入的备注信息（如果有）
        String remark = getIntent().getStringExtra("remark");
        if (remark != null && !remark.equals("点击添加备注")) {
            etRemark.setText(remark);
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
                // 获取输入的备注
                String remark = etRemark.getText().toString().trim();

                // 返回结果
                Intent intent = new Intent();
                intent.putExtra("remark", remark);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }
}