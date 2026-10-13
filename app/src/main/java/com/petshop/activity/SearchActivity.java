package com.petshop.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.petshop.R;
import com.petshop.adapter.SearchHistoryAdapter;
import com.petshop.adapter.SearchSuggestionAdapter;
import com.petshop.databinding.ActivitySearchBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchActivity extends AppCompatActivity {

    private ActivitySearchBinding binding;
    private SearchHistoryAdapter historyAdapter;
    private SearchSuggestionAdapter suggestionAdapter;
    private List<String> historyList, suggestionList, hotKeywords;
    private static final int MAX_HISTORY_COUNT = 5;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initData();
        initListeners();
    }

    private void initData() {
        android.util.Log.d("SearchActivity", "initData started");
        sp = getSharedPreferences("search_history", MODE_PRIVATE);
        historyList = getSearchHistory();
        hotKeywords = getHotKeywords();
        suggestionList = new ArrayList<>();

        // 历史搜索适配器
        historyAdapter = new SearchHistoryAdapter(historyList, new SearchHistoryAdapter.OnHistoryItemClickListener() {
            @Override
            public void onItemClick(String keyword) {
                performSearch(keyword);
            }

            @Override
            public void onDeleteClick(String keyword) {
                deleteHistory(keyword);
            }
        });
        binding.rvHistory.setLayoutManager(new GridLayoutManager(this, 3));
        binding.rvHistory.setAdapter(historyAdapter);

        // 热门关键词适配器
        binding.rvHot.setLayoutManager(new GridLayoutManager(this, 3));
        binding.rvHot.setAdapter(new SearchHotAdapter(hotKeywords, new SearchHotAdapter.OnHotItemClickListener() {
            @Override
            public void onItemClick(String keyword) {
                binding.etSearch.setText(keyword);
                performSearch(keyword);
            }
        }));

        // 搜索建议适配器
        suggestionAdapter = new SearchSuggestionAdapter(suggestionList, new SearchSuggestionAdapter.OnSuggestionItemClickListener() {
            @Override
            public void onItemClick(String keyword) {
                binding.etSearch.setText(keyword);
                performSearch(keyword);
            }
        });
        binding.rvSuggestion.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSuggestion.setAdapter(suggestionAdapter);

        // 显示键盘
        binding.etSearch.requestFocus();
    }

    private void initListeners() {
        binding.ivBack.setOnClickListener(v -> finish());

        binding.ivClear.setOnClickListener(v -> {
            binding.etSearch.setText("");
            showHistoryAndHot();
        });

        binding.ivSearch.setOnClickListener(v -> {
            String keyword = binding.etSearch.getText().toString().trim();
            if (!keyword.isEmpty()) {
                performSearch(keyword);
            }
        });

        binding.tvClearHistory.setOnClickListener(v -> clearAllHistory());

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String keyword = s.toString().trim();
                if (keyword.isEmpty()) {
                    showHistoryAndHot();
                } else {
                    showSuggestions(keyword);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performSearch(String keyword) {
        saveSearchHistory(keyword);
        Intent intent = new Intent(this, SearchResultActivity.class);
        intent.putExtra("keyword", keyword);
        startActivity(intent);
    }

    private void showHistoryAndHot() {
        binding.llHistory.setVisibility(View.VISIBLE);
        binding.llHot.setVisibility(View.VISIBLE);
        binding.rvSuggestion.setVisibility(View.GONE);
        binding.ivClear.setVisibility(binding.etSearch.getText().toString().isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showSuggestions(String keyword) {
        binding.llHistory.setVisibility(View.GONE);
        binding.llHot.setVisibility(View.GONE);
        binding.rvSuggestion.setVisibility(View.VISIBLE);
        binding.ivClear.setVisibility(View.VISIBLE);

        // 模拟搜索建议，忽略大小写
        suggestionList.clear();
        String lowerKeyword = keyword.toLowerCase();
        for (String hotKeyword : hotKeywords) {
            if (hotKeyword.toLowerCase().contains(lowerKeyword)) {
                suggestionList.add(hotKeyword);
            }
        }
        suggestionAdapter.notifyDataSetChanged();
    }

    private List<String> getSearchHistory() {
        Set<String> historySet = sp.getStringSet("history", new HashSet<>());
        List<String> history = new ArrayList<>(historySet);
        // 反转列表，确保最新的搜索记录在最前面
        java.util.Collections.reverse(history);
        return history;
    }

    private void saveSearchHistory(String keyword) {
        List<String> history = getSearchHistory();
        history.remove(keyword); // 先移除旧的
        history.add(0, keyword); // 再添加到最前面

        // 限制历史记录数量
        if (history.size() > MAX_HISTORY_COUNT) {
            history = history.subList(0, MAX_HISTORY_COUNT);
        }

        // 保存到SharedPreferences
        Set<String> historySet = new HashSet<>(history);
        sp.edit().putStringSet("history", historySet).apply();
        historyList = history;
        historyAdapter.notifyDataSetChanged();
        if (binding.rvHistory.getAdapter() != null) {
            binding.rvHistory.getAdapter().notifyDataSetChanged();
        }
    }

    private void deleteHistory(String keyword) {
        List<String> history = getSearchHistory();
        history.remove(keyword);
        Set<String> historySet = new HashSet<>(history);
        sp.edit().putStringSet("history", historySet).apply();
        historyList = history;
        historyAdapter.notifyDataSetChanged();
    }

    private void clearAllHistory() {
        // 显示确认对话框
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("清空搜索历史")
            .setMessage("确定要清空所有搜索历史吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                sp.edit().clear().apply();
                historyList.clear();
                historyAdapter.notifyDataSetChanged();
                Toast.makeText(this, "历史记录已清空", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private List<String> getHotKeywords() {
        // 根据数据库商品名称设置热门搜索词，确保能搜索到实际商品
        List<String> allKeywords = new ArrayList<>();
        
        // 食品类
        allKeywords.add("狗粮");
        allKeywords.add("猫粮");
        allKeywords.add("猫罐头");
        allKeywords.add("羊奶粉");
        allKeywords.add("磨牙棒");
        allKeywords.add("小鱼干");
        allKeywords.add("冻干");
        
        // 玩具类
        allKeywords.add("逗猫棒");
        allKeywords.add("猫抓板");
        allKeywords.add("磨牙球");
        allKeywords.add("飞盘");
        allKeywords.add("漏食球");
        
        // 窝垫类
        allKeywords.add("狗窝");
        allKeywords.add("猫窝");
        allKeywords.add("宠物垫子");
        
        // 清洁护理
        allKeywords.add("沐浴露");
        allKeywords.add("梳子");
        allKeywords.add("指甲剪");
        allKeywords.add("湿巾");
        
        // 医疗健康
        allKeywords.add("驱虫药");
        allKeywords.add("益生菌");
        allKeywords.add("眼药水");
        
        // 服饰配件
        allKeywords.add("雨衣");
        allKeywords.add("牵引绳");
        allKeywords.add("项圈");
        
        // 用品类
        allKeywords.add("饮水机");
        allKeywords.add("喂食器");
        allKeywords.add("猫包");
        allKeywords.add("航空箱");
        
        // 随机选择9个作为热门关键词
        Collections.shuffle(allKeywords);
        return allKeywords.subList(0, Math.min(9, allKeywords.size()));
    }

    // 热门关键词适配器
    static class SearchHotAdapter extends RecyclerView.Adapter<SearchHotAdapter.HotViewHolder> {
        private List<String> hotKeywords;
        private OnHotItemClickListener listener;

        public SearchHotAdapter(List<String> hotKeywords, OnHotItemClickListener listener) {
            this.hotKeywords = hotKeywords;
            this.listener = listener;
        }

        @Override
        public HotViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_hot, parent, false);
            return new HotViewHolder(view);
        }

        @Override
        public void onBindViewHolder(HotViewHolder holder, int position) {
            String keyword = hotKeywords.get(position);
            holder.tvKeyword.setText(keyword);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onItemClick(keyword);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return hotKeywords.size();
        }

        class HotViewHolder extends RecyclerView.ViewHolder {
            TextView tvKeyword;

            public HotViewHolder(View itemView) {
                super(itemView);
                tvKeyword = itemView.findViewById(R.id.tv_keyword);
            }
        }

        interface OnHotItemClickListener {
            void onItemClick(String keyword);
        }
    }
}
