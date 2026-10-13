package com.petshop.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.petshop.R;
import com.petshop.adapter.IntroAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 引导页Activity
 * 首次启动应用时显示，介绍应用的主要功能
 */
public class IntroActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private LinearLayout dotsLayout;
    private Button btnSkip;
    private Button btnNext;

    private List<IntroItem> introItems;
    private int currentPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        initView();
        initData();
        setupViewPager();
        updateUI();
    }

    private void initView() {
        viewPager = findViewById(R.id.view_pager);
        dotsLayout = findViewById(R.id.dots_layout);
        btnSkip = findViewById(R.id.btn_skip);
        btnNext = findViewById(R.id.btn_next);

        btnSkip.setOnClickListener(v -> finishIntro());
        btnNext.setOnClickListener(v -> {
            if (currentPosition >= introItems.size() - 1) {
                finishIntro();
                return;
            }
            viewPager.setCurrentItem(currentPosition + 1, true);
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                updateUI();
            }
        });
    }

    private void initData() {
        introItems = new ArrayList<>();
        introItems.add(new IntroItem(
                R.drawable.bg_intro_badge_1,
                R.drawable.ic_paw,
                "欢迎来到宠物乐园",
                "为您的爱宠挑好物\n从今天开始，给它更好的陪伴"
        ));
        introItems.add(new IntroItem(
                R.drawable.bg_intro_badge_2,
                R.drawable.ic_comment,
                "分享养宠日常",
                "在社区和其他铲屎官交流经验\n晒晒你家的毛孩子"
        ));
        introItems.add(new IntroItem(
                R.drawable.bg_intro_badge_3,
                R.drawable.ic_order_pending_receipt,
                "预约服务更省心",
                "宠物医疗、美容护理线上预约\n到店即享专属服务"
        ));
        introItems.add(new IntroItem(
                R.drawable.bg_intro_badge_4,
                R.drawable.ic_like,
                "开启你的养宠之旅",
                "登录后即可下单、预约、发动态\n一切从这里开始"
        ));
    }

    private void setupViewPager() {
        IntroAdapter adapter = new IntroAdapter(introItems);
        viewPager.setAdapter(adapter);
    }

    private void updateUI() {
        updateDots();

        // 最后一页把「下一步」换成「开始体验」，统一用同一个按钮避免重叠
        boolean isLastPage = currentPosition == introItems.size() - 1;
        btnNext.setText(isLastPage ? "开始体验" : "下一步");
        btnSkip.setVisibility(isLastPage ? View.INVISIBLE : View.VISIBLE);
    }

    private void updateDots() {
        // 指示点在布局里静态定义（数量与引导页一致），这里只切换当前页的胶囊与小圆点
        for (int i = 0; i < dotsLayout.getChildCount(); i++) {
            View dot = dotsLayout.getChildAt(i);
            boolean active = i == currentPosition;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) dot.getLayoutParams();
            params.width = dp(active ? 24 : 8);
            params.height = dp(8);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(active
                    ? R.drawable.dot_indicator_active
                    : R.drawable.dot_indicator_inactive);
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void finishIntro() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public static class IntroItem {
        private int badgeResId;
        private int imageResId;
        private String title;
        private String description;

        public IntroItem(int badgeResId, int imageResId, String title, String description) {
            this.badgeResId = badgeResId;
            this.imageResId = imageResId;
            this.title = title;
            this.description = description;
        }

        public int getBadgeResId() {
            return badgeResId;
        }

        public int getImageResId() {
            return imageResId;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }
    }
}
