package com.petshop.activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.petshop.R;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.util.AuthUtil;
import com.petshop.util.GlideUtil;
import com.petshop.util.ImageCompressor;
import com.petshop.util.LogUtil;
import com.petshop.util.ToastUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 添加 / 编辑宠物：照片、名字、大类与具体品种、性别、生日、体重、备注。
 * 带 EXTRA_PET_ID 进来就是编辑已有宠物，否则是新增。
 */
public class PetEditActivity extends AppCompatActivity {

    private static final String TAG = "PetEditActivity";

    public static final String EXTRA_PET_ID = "pet_id";
    public static final String EXTRA_NAME = "pet_name";
    public static final String EXTRA_SPECIES = "pet_species";
    public static final String EXTRA_BREED = "pet_breed";
    public static final String EXTRA_GENDER = "pet_gender";
    public static final String EXTRA_BIRTHDAY = "pet_birthday";
    public static final String EXTRA_WEIGHT = "pet_weight";
    public static final String EXTRA_NOTE = "pet_note";
    public static final String EXTRA_HAS_AVATAR = "pet_has_avatar";
    public static final String EXTRA_UPDATED_AT = "pet_updated_at";

    /** 品种接口拿不到时的兜底，保证离线也能选几个常见品种 */
    private static final Map<String, List<String>> FALLBACK_BREEDS = new HashMap<>();

    static {
        FALLBACK_BREEDS.put(ApiService.PetItem.SPECIES_CAT, Arrays.asList(
                "中华田园猫", "狸花猫", "橘猫", "英国短毛猫", "布偶猫", "缅因猫", "暹罗猫", "波斯猫"));
        FALLBACK_BREEDS.put(ApiService.PetItem.SPECIES_DOG, Arrays.asList(
                "比格犬", "金毛寻回犬", "拉布拉多", "柯基", "柴犬", "哈士奇", "萨摩耶", "泰迪"));
        FALLBACK_BREEDS.put(ApiService.PetItem.SPECIES_OTHER, Arrays.asList(
                "垂耳兔", "仓鼠", "龙猫", "鹦鹉", "巴西龟", "金鱼"));
    }

    private ImageView ivAvatar;
    private TextView tvChangePhoto, tvRemovePhoto;
    private EditText etName, etWeight, etNote;
    private TextView tvSpeciesCat, tvSpeciesDog, tvSpeciesOther;
    private TextView tvBreedValue, tvGenderMale, tvGenderFemale, tvGenderUnknown;
    private TextView tvBirthdayValue, tvBirthdayClear, tvDelete;
    private MaterialButton btnSave;

    private final Map<String, List<String>> breeds = new HashMap<>();
    private final List<String> breedOptions = new ArrayList<>();
    private ActivityResultLauncher<String> pickPhotoLauncher;
    private ExecutorService executor;
    private boolean saving = false;

    private int petId = 0;
    private boolean hasAvatar = false;
    private String updatedAt = "";
    private String species = ApiService.PetItem.SPECIES_CAT;
    private String gender = ApiService.PetItem.GENDER_UNKNOWN;
    private String breed = "";
    private String birthday = "";
    /** 新选的照片，保存时才上传 */
    private Uri pickedPhoto;
    /** 编辑时主动移除了原照片 */
    private boolean avatarRemoved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pet_edit);

        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }

        initExtras();
        initViews();
        initPhotoPicker();

        executor = Executors.newSingleThreadExecutor();
        renderAvatar();
        updateChipStates();
        updateBirthdayText();
        loadBreeds();
    }

    private void initExtras() {
        petId = getIntent().getIntExtra(EXTRA_PET_ID, 0);
        species = safe(getIntent().getStringExtra(EXTRA_SPECIES), ApiService.PetItem.SPECIES_CAT);
        gender = safe(getIntent().getStringExtra(EXTRA_GENDER), ApiService.PetItem.GENDER_UNKNOWN);
        breed = safe(getIntent().getStringExtra(EXTRA_BREED), "");
        birthday = safe(getIntent().getStringExtra(EXTRA_BIRTHDAY), "");
        hasAvatar = getIntent().getBooleanExtra(EXTRA_HAS_AVATAR, false);
        updatedAt = safe(getIntent().getStringExtra(EXTRA_UPDATED_AT), "");
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back_dark);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(petId > 0 ? "编辑宠物" : "添加宠物");

        ivAvatar = findViewById(R.id.iv_pet_avatar);
        tvChangePhoto = findViewById(R.id.tv_change_photo);
        tvRemovePhoto = findViewById(R.id.tv_remove_photo);
        etName = findViewById(R.id.et_name);
        etWeight = findViewById(R.id.et_weight);
        etNote = findViewById(R.id.et_note);
        tvSpeciesCat = findViewById(R.id.tv_species_cat);
        tvSpeciesDog = findViewById(R.id.tv_species_dog);
        tvSpeciesOther = findViewById(R.id.tv_species_other);
        tvBreedValue = findViewById(R.id.tv_breed_value);
        tvGenderMale = findViewById(R.id.tv_gender_male);
        tvGenderFemale = findViewById(R.id.tv_gender_female);
        tvGenderUnknown = findViewById(R.id.tv_gender_unknown);
        tvBirthdayValue = findViewById(R.id.tv_birthday_value);
        tvBirthdayClear = findViewById(R.id.tv_birthday_clear);
        tvDelete = findViewById(R.id.tv_delete);
        btnSave = findViewById(R.id.btn_save);

        if (petId > 0) {
            etName.setText(safe(getIntent().getStringExtra(EXTRA_NAME), ""));
            String weight = safe(getIntent().getStringExtra(EXTRA_WEIGHT), "");
            if (weight.endsWith(".0")) {
                weight = weight.substring(0, weight.length() - 2);
            }
            etWeight.setText(weight);
            etNote.setText(safe(getIntent().getStringExtra(EXTRA_NOTE), ""));
            tvDelete.setVisibility(View.VISIBLE);
            tvChangePhoto.setText("更换照片");
        }
        tvBreedValue.setText(breed);

        tvChangePhoto.setOnClickListener(v -> pickPhotoLauncher.launch("image/*"));
        tvRemovePhoto.setOnClickListener(v -> {
            pickedPhoto = null;
            avatarRemoved = true;
            renderAvatar();
        });
        tvSpeciesCat.setOnClickListener(v -> selectSpecies(ApiService.PetItem.SPECIES_CAT));
        tvSpeciesDog.setOnClickListener(v -> selectSpecies(ApiService.PetItem.SPECIES_DOG));
        tvSpeciesOther.setOnClickListener(v -> selectSpecies(ApiService.PetItem.SPECIES_OTHER));
        tvGenderMale.setOnClickListener(v -> selectGender(ApiService.PetItem.GENDER_MALE));
        tvGenderFemale.setOnClickListener(v -> selectGender(ApiService.PetItem.GENDER_FEMALE));
        tvGenderUnknown.setOnClickListener(v -> selectGender(ApiService.PetItem.GENDER_UNKNOWN));
        findViewById(R.id.ll_breed).setOnClickListener(v -> showBreedPicker());
        findViewById(R.id.ll_birthday).setOnClickListener(v -> showDatePicker());
        tvBirthdayClear.setOnClickListener(v -> {
            birthday = "";
            updateBirthdayText();
        });
        tvDelete.setOnClickListener(v -> confirmDelete());
        btnSave.setOnClickListener(v -> save());
    }

    private void initPhotoPicker() {
        pickPhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri == null) {
                        return;
                    }
                    pickedPhoto = uri;
                    avatarRemoved = false;
                    renderAvatar();
                });
    }

    // ---------------- 照片 ----------------

    private void renderAvatar() {
        boolean hasPhoto = pickedPhoto != null || (hasAvatar && !avatarRemoved);
        tvRemovePhoto.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        tvChangePhoto.setText(hasPhoto ? "更换照片" : "添加照片");

        if (pickedPhoto != null) {
            Glide.with(this).load(pickedPhoto).circleCrop().into(ivAvatar);
        } else if (hasAvatar && !avatarRemoved) {
            GlideUtil.loadAvatar(this, buildAvatarUrl(), ivAvatar);
        } else {
            // 清空图片，露出背景里的渐变爪印
            ivAvatar.setImageDrawable(null);
        }
    }

    private String buildAvatarUrl() {
        String baseUrl = ApiClient.getCurrentBaseUrl(this);
        String url = (baseUrl == null ? "" : baseUrl) + "pets/" + petId + "/avatar";
        if (!updatedAt.isEmpty()) {
            url = url + "?v=" + updatedAt.replace(' ', '_').replace(':', '-');
        }
        return url;
    }

    // ---------------- 大类 / 品种 / 性别 / 生日 ----------------

    private void selectSpecies(String value) {
        if (!species.equals(value)) {
            // 换了大类，原来选的品种大概率不适用，清掉重新选
            species = value;
            breed = "";
            tvBreedValue.setText("");
        }
        updateChipStates();
    }

    private void selectGender(String value) {
        gender = value;
        updateChipStates();
    }

    private void updateChipStates() {
        applyChip(tvSpeciesCat, ApiService.PetItem.SPECIES_CAT.equals(species));
        applyChip(tvSpeciesDog, ApiService.PetItem.SPECIES_DOG.equals(species));
        applyChip(tvSpeciesOther, ApiService.PetItem.SPECIES_OTHER.equals(species));
        applyChip(tvGenderMale, ApiService.PetItem.GENDER_MALE.equals(gender));
        applyChip(tvGenderFemale, ApiService.PetItem.GENDER_FEMALE.equals(gender));
        applyChip(tvGenderUnknown, ApiService.PetItem.GENDER_UNKNOWN.equals(gender));
    }

    private void applyChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.bg_pet_chip_selected
                : R.drawable.bg_pet_chip_normal);
        chip.setTextColor(getResources().getColor(selected
                ? R.color.colorPrimary
                : R.color.colorTextSecondary));
    }

    /** 品种字典由后端提供，取不到就用内置兜底 */
    private void loadBreeds() {
        ApiClient.getInstance(this).getApiService().getPetBreeds()
                .enqueue(new Callback<ApiService.Response<Map<String, List<String>>>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Map<String, List<String>>>> call,
                                           Response<ApiService.Response<Map<String, List<String>>>> response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200 && response.body().getData() != null) {
                            breeds.clear();
                            breeds.putAll(response.body().getData());
                        } else {
                            breeds.clear();
                            breeds.putAll(FALLBACK_BREEDS);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Map<String, List<String>>>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        LogUtil.e(TAG, "加载品种字典失败", t);
                        breeds.clear();
                        breeds.putAll(FALLBACK_BREEDS);
                    }
                });
    }

    private void showBreedPicker() {
        breedOptions.clear();
        List<String> list = breeds.get(species);
        if (list == null || list.isEmpty()) {
            list = FALLBACK_BREEDS.get(species);
        }
        if (list != null) {
            breedOptions.addAll(list);
        }
        breedOptions.add("自定义品种…");

        String[] items = breedOptions.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("选择具体品种")
                .setItems(items, (dialog, which) -> {
                    if (which == breedOptions.size() - 1) {
                        showCustomBreedDialog();
                    } else {
                        breed = breedOptions.get(which);
                        tvBreedValue.setText(breed);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCustomBreedDialog() {
        EditText input = new EditText(this);
        input.setHint("例：曼基康矮脚猫");
        input.setText(breed);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("自定义品种")
                .setView(input, dp(20), dp(8), dp(20), 0)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        return;
                    }
                    breed = value;
                    tvBreedValue.setText(breed);
                })
                .show();
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        if (!birthday.isEmpty()) {
            String[] parts = birthday.split("-");
            if (parts.length == 3) {
                try {
                    calendar.set(Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]) - 1,
                            Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {
                    // 格式不对就退回今天
                }
            }
        }
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            birthday = String.format(java.util.Locale.CHINA, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            updateBirthdayText();
        },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateBirthdayText() {
        tvBirthdayValue.setText(birthday);
        tvBirthdayClear.setVisibility(birthday.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---------------- 保存 / 删除 ----------------

    private void save() {
        if (saving) {
            return;
        }
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            ToastUtil.showWarning(this, "给毛孩子起个名字吧");
            return;
        }
        String weightText = etWeight.getText().toString().trim();
        if (!weightText.isEmpty()) {
            try {
                double value = Double.parseDouble(weightText);
                if (value <= 0 || value > 200) {
                    ToastUtil.showWarning(this, "体重要填 0.1 ~ 200 之间的数字");
                    return;
                }
            } catch (NumberFormatException e) {
                ToastUtil.showWarning(this, "体重要填数字，比如 4.2");
                return;
            }
        }

        saving = true;
        btnSave.setEnabled(false);
        btnSave.setText("保存中…");

        final Uri photo = pickedPhoto;
        final boolean removed = avatarRemoved;
        executor.execute(() -> {
            List<MultipartBody.Part> parts = new ArrayList<>();
            if (photo != null) {
                File compressed = ImageCompressor.compressAvatar(PetEditActivity.this, photo);
                if (compressed != null && compressed.exists()) {
                    RequestBody fileBody = RequestBody.create(MediaType.parse("image/jpeg"), compressed);
                    parts.add(MultipartBody.Part.createFormData("avatar", compressed.getName(), fileBody));
                }
            }
            runOnUiThread(() -> submit(name, weightText, removed, parts));
        });
    }

    private void submit(String name, String weightText, boolean removed, List<MultipartBody.Part> parts) {
        RequestBody nameBody = textBody(name);
        RequestBody speciesBody = textBody(species);
        RequestBody breedBody = textBody(breed);
        RequestBody genderBody = textBody(gender);
        RequestBody birthdayBody = textBody(birthday);
        RequestBody weightBody = textBody(weightText);
        RequestBody noteBody = textBody(etNote.getText().toString().trim());

        boolean fromPet = petId > 0;
        ApiService api = ApiClient.getInstance(this).getApiService();
        Call<ApiService.Response<ApiService.PetItem>> call;
        if (fromPet) {
            // 已经上传了新照片就没必要再告诉后端“清空头像”
            RequestBody removeBody = textBody(removed && parts.isEmpty() ? "true" : "false");
            call = api.updatePet(petId, nameBody, speciesBody, breedBody, genderBody,
                    birthdayBody, weightBody, noteBody, removeBody, parts);
        } else {
            call = api.createPet(nameBody, speciesBody, breedBody, genderBody,
                    birthdayBody, weightBody, noteBody, parts);
        }

        call.enqueue(new Callback<ApiService.Response<ApiService.PetItem>>() {
            @Override
            public void onResponse(Call<ApiService.Response<ApiService.PetItem>> call,
                                   Response<ApiService.Response<ApiService.PetItem>> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 200) {
                    ToastUtil.showSuccess(PetEditActivity.this,
                            TextUtils.isEmpty(response.body().getMessage())
                                    ? "已保存" : response.body().getMessage());
                    setResult(RESULT_OK);
                    finish();
                } else {
                    resetSaveButton();
                    String message = response.body() == null
                            ? "保存失败" : response.body().getMessage();
                    ToastUtil.showError(PetEditActivity.this, message);
                }
            }

            @Override
            public void onFailure(Call<ApiService.Response<ApiService.PetItem>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                resetSaveButton();
                LogUtil.e(TAG, "保存宠物失败", t);
                ToastUtil.showError(PetEditActivity.this, "网络异常，请稍后重试");
            }
        });
    }

    private void confirmDelete() {
        if (petId <= 0) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除这只宠物")
                .setMessage("删除后档案就找不回来了，确定吗？")
                .setNegativeButton("再想想", null)
                .setPositiveButton("删除", (dialog, which) -> deletePet())
                .show();
    }

    private void deletePet() {
        if (saving) {
            return;
        }
        saving = true;
        ApiClient.getInstance(this).getApiService().deletePet(petId)
                .enqueue(new Callback<ApiService.Response<Void>>() {
                    @Override
                    public void onResponse(Call<ApiService.Response<Void>> call,
                                           Response<ApiService.Response<Void>> response) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 200) {
                            ToastUtil.showSuccess(PetEditActivity.this, "已删除");
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            saving = false;
                            String message = response.body() == null
                                    ? "删除失败" : response.body().getMessage();
                            ToastUtil.showError(PetEditActivity.this, message);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiService.Response<Void>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        saving = false;
                        LogUtil.e(TAG, "删除宠物失败", t);
                        ToastUtil.showError(PetEditActivity.this, "网络异常，请稍后重试");
                    }
                });
    }

    private void resetSaveButton() {
        saving = false;
        btnSave.setEnabled(true);
        btnSave.setText("保存");
    }

    private RequestBody textBody(String value) {
        return RequestBody.create(MediaType.parse("text/plain"), value == null ? "" : value);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
