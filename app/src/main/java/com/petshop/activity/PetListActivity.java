package com.petshop.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.petshop.R;
import com.petshop.adapter.PetAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AuthUtil;
import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 我的宠物：展示当前账号下的宠物档案，可新增、点开编辑
 */
public class PetListActivity extends AppCompatActivity {

    private static final String TAG = "PetListActivity";

    private RecyclerView rvPets;
    private View llEmpty;
    private PetAdapter adapter;
    private ActivityResultLauncher<Intent> editLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pet_list);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back_dark);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle("我的宠物");

        rvPets = findViewById(R.id.rv_pets);
        llEmpty = findViewById(R.id.ll_empty);
        MaterialButton btnAdd = findViewById(R.id.btn_add_pet);

        adapter = new PetAdapter(this, this::openEdit);
        rvPets.setLayoutManager(new LinearLayoutManager(this));
        rvPets.setAdapter(adapter);

        editLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> loadPets());

        btnAdd.setOnClickListener(v -> editLauncher.launch(new Intent(this, PetEditActivity.class)));
        loadPets();
    }

    private void openEdit(ApiService.PetItem item) {
        Intent intent = new Intent(this, PetEditActivity.class);
        intent.putExtra(PetEditActivity.EXTRA_PET_ID, item.getId());
        intent.putExtra(PetEditActivity.EXTRA_NAME, item.getName());
        intent.putExtra(PetEditActivity.EXTRA_SPECIES, item.getSpecies());
        intent.putExtra(PetEditActivity.EXTRA_BREED, item.getBreed());
        intent.putExtra(PetEditActivity.EXTRA_GENDER, item.getGender());
        intent.putExtra(PetEditActivity.EXTRA_BIRTHDAY, item.getBirthday());
        intent.putExtra(PetEditActivity.EXTRA_WEIGHT, item.getWeight() == null ? "" : String.valueOf(item.getWeight()));
        intent.putExtra(PetEditActivity.EXTRA_NOTE, item.getNote());
        intent.putExtra(PetEditActivity.EXTRA_HAS_AVATAR, item.isHasAvatar());
        intent.putExtra(PetEditActivity.EXTRA_UPDATED_AT, item.getUpdatedAt());
        editLauncher.launch(intent);
    }

    private void loadPets() {
        ApiClient.getInstance(this).getApiService().getPets()
                .enqueue(new Callback<ApiService.Response<List<ApiService.PetItem>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<List<ApiService.PetItem>>> call,
                                           Response<ApiService.Response<List<ApiService.PetItem>>> response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            List<ApiService.PetItem> pets = response.body().getData();
                            adapter.setItems(pets);
                            boolean empty = pets == null || pets.isEmpty();
                            llEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                            rvPets.setVisibility(empty ? View.GONE : View.VISIBLE);
                        } else {
                            String message = response.body() == null
                                    ? "加载失败" : response.body().getMessage();
                            ToastUtil.showError(PetListActivity.this, message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<List<ApiService.PetItem>>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        LogUtil.e(TAG, "加载宠物列表失败", t);
                        ToastUtil.showError(PetListActivity.this, "网络异常，请稍后重试");
                    }
                });
    }
}
