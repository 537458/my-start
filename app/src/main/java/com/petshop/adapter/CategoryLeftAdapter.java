package com.petshop.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.petshop.R;
import com.petshop.model.Category;
import com.petshop.util.DiffUtilCallback;

import java.util.List;

public class CategoryLeftAdapter extends RecyclerView.Adapter<CategoryLeftAdapter.ViewHolder> {

    private List<Category> categories;
    private int selectedPosition = 0;
    private OnItemClickListener onItemClickListener;

    public CategoryLeftAdapter(List<Category> categories) {
        this.categories = categories;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void updateData(List<Category> newCategories) {
        DiffUtilCallback<Category> diffCallback = new DiffUtilCallback<>(
                this.categories,
                newCategories,
                new DiffUtilCallback.ItemCallback<Category>() {
                    @Override
                    public boolean areItemsTheSame(Category oldItem, Category newItem) {
                        return oldItem.getId() == newItem.getId();
                    }

                    @Override
                    public boolean areContentsTheSame(Category oldItem, Category newItem) {
                        return oldItem.getName().equals(newItem.getName());
                    }
                }
        );

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        this.categories = newCategories;
        diffResult.dispatchUpdatesTo(this);
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_left, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.tvName.setText(category.getName());
        
        if (position == selectedPosition) {
            holder.itemView.setBackgroundColor(0xFFFFFFFF);
            holder.tvName.setTextColor(0xFFF57C00);
        } else {
            holder.itemView.setBackgroundColor(0xFFF5F5F5);
            holder.tvName.setTextColor(0xFF333333);
        }
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_category_name);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }
}
