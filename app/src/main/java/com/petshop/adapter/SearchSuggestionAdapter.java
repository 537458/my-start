package com.petshop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.petshop.R;

import java.util.List;

public class SearchSuggestionAdapter extends RecyclerView.Adapter<SearchSuggestionAdapter.SuggestionViewHolder> {

    private List<String> suggestionList;
    private OnSuggestionItemClickListener listener;

    public SearchSuggestionAdapter(List<String> suggestionList, OnSuggestionItemClickListener listener) {
        this.suggestionList = suggestionList;
        this.listener = listener;
    }

    @Override
    public SuggestionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_suggestion, parent, false);
        return new SuggestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SuggestionViewHolder holder, int position) {
        String keyword = suggestionList.get(position);
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
        return suggestionList.size();
    }

    class SuggestionViewHolder extends RecyclerView.ViewHolder {
        TextView tvKeyword;

        public SuggestionViewHolder(View itemView) {
            super(itemView);
            tvKeyword = itemView.findViewById(R.id.tv_keyword);
        }
    }

    public interface OnSuggestionItemClickListener {
        void onItemClick(String keyword);
    }
}
