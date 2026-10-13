package com.petshop.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.petshop.R;
import com.petshop.databinding.ActivityMainBinding;
import com.petshop.fragment.CartFragment;
import com.petshop.fragment.CategoryFragment;
import com.petshop.fragment.CommunityFragment;
import com.petshop.fragment.HomeFragment;
import com.petshop.fragment.ProfileFragment;

public class MainActivity extends AppCompatActivity {

    /** 启动参数：打开指定底部页签，目前支持 home（商城首页） */
    public static final String EXTRA_OPEN_TAB = "open_tab";
    public static final String TAB_HOME = "home";

    private ActivityMainBinding binding;
    private FragmentManager fragmentManager;
    private Fragment currentFragment;

    private HomeFragment homeFragment;
    private CategoryFragment categoryFragment;
    private CommunityFragment communityFragment;
    private CartFragment cartFragment;
    private ProfileFragment profileFragment;

    private int lastSelectedItemId = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fragmentManager = getSupportFragmentManager();

        homeFragment = new HomeFragment();
        categoryFragment = new CategoryFragment();
        communityFragment = new CommunityFragment();
        cartFragment = new CartFragment();
        profileFragment = new ProfileFragment();

        switchFragment(homeFragment);

        handleOpenTab(getIntent());

        binding.bnvMain.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                
                if (id != lastSelectedItemId) {
                    animateNavItem(lastSelectedItemId, false);
                    animateNavItem(id, true);
                    lastSelectedItemId = id;
                }
                
                if (id == R.id.nav_home) {
                    switchFragment(homeFragment);
                    return true;
                } else if (id == R.id.nav_category) {
                    switchFragment(categoryFragment);
                    return true;
                } else if (id == R.id.nav_community) {
                    switchFragment(communityFragment);
                    return true;
                } else if (id == R.id.nav_cart) {
                    switchFragment(cartFragment);
                    return true;
                } else if (id == R.id.nav_profile) {
                    switchFragment(profileFragment);
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    /**
     * 其他页面通过 Intent 跳到主导航时，可指定要展示的页签。
     * 例如优惠券页“去使用”直接回到商城首页，而不是回到个人中心。
     */
    private void handleOpenTab(android.content.Intent intent) {
        if (intent == null) {
            return;
        }
        String tab = intent.getStringExtra(EXTRA_OPEN_TAB);
        if (TAB_HOME.equals(tab)) {
            switchToHome();
            // 消费掉参数，避免旋转屏幕重建后重复切换
            intent.removeExtra(EXTRA_OPEN_TAB);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenTab(intent);
    }

    private void animateNavItem(int menuItemId, boolean isSelected) {
        BottomNavigationView bnv = binding.bnvMain;
        View itemView = bnv.findViewById(menuItemId);
        if (itemView == null) return;

        Animation anim;
        if (isSelected) {
            anim = AnimationUtils.loadAnimation(this, R.anim.nav_item_select);
        } else {
            anim = AnimationUtils.loadAnimation(this, R.anim.nav_item_deselect);
        }
        itemView.startAnimation(anim);
    }

    @Override
    public void onBackPressed() {
        if (currentFragment != homeFragment) {
            switchFragment(homeFragment);
            if (lastSelectedItemId != R.id.nav_home) {
                animateNavItem(lastSelectedItemId, false);
                animateNavItem(R.id.nav_home, true);
                lastSelectedItemId = R.id.nav_home;
            }
            binding.bnvMain.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    private void switchFragment(Fragment targetFragment) {
        if (currentFragment == targetFragment) {
            return;
        }
    
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(
                R.anim.fragment_enter,
                R.anim.fragment_exit,
                R.anim.fragment_enter,
                R.anim.fragment_exit
        );
    
        if (!targetFragment.isAdded()) {
            transaction.add(R.id.fl_content, targetFragment);
        }
    
        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }
    
        transaction.show(targetFragment);
        transaction.commitAllowingStateLoss();
    
        currentFragment = targetFragment;
        applyTitleFor(targetFragment);
    }

    /**
     * 顶部标题跟随页签切换：首页保留应用名，其余页显示自身名称。
     * 集中在这里处理，避免各个 Fragment 各自改标题导致状态错乱。
     */
    private void applyTitleFor(Fragment targetFragment) {
        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        int titleRes;
        if (targetFragment == categoryFragment) {
            titleRes = R.string.category;
        } else if (targetFragment == communityFragment) {
            titleRes = R.string.community;
        } else if (targetFragment == cartFragment) {
            titleRes = R.string.cart;
        } else if (targetFragment == profileFragment) {
            titleRes = R.string.profile;
        } else {
            titleRes = R.string.app_name;
        }
        actionBar.setTitle(titleRes);
    }
    
    /** 供子 Fragment 切回首页（例如购物车空状态“去逛逛”） */
    public void switchToHome() {
        switchFragment(homeFragment);

        if (lastSelectedItemId != R.id.nav_home) {
            animateNavItem(lastSelectedItemId, false);
            animateNavItem(R.id.nav_home, true);
            lastSelectedItemId = R.id.nav_home;
        }
        binding.bnvMain.setSelectedItemId(R.id.nav_home);
    }

    public void switchToCategoryFragment(int categoryIndex) {
        switchFragment(categoryFragment);

        if (lastSelectedItemId != R.id.nav_category) {
            animateNavItem(lastSelectedItemId, false);
            animateNavItem(R.id.nav_category, true);
            lastSelectedItemId = R.id.nav_category;
        }
        binding.bnvMain.setSelectedItemId(R.id.nav_category);
        
        if (categoryFragment != null) {
            categoryFragment.selectCategory(categoryIndex);
        }
    }
}
