package com.petshop.adapter;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/**
 * 通用ViewHolder基类
 * 作为所有ViewHolder的基类，提供统一的构造方法
 */
public class BaseViewHolder extends RecyclerView.ViewHolder {

    public BaseViewHolder(View itemView) {
        super(itemView);
    }
}
