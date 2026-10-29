package com.petshop.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.petshop.util.DiffUtilCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用适配器基类
 * 提取适配器的重复代码模式，减少代码重复
 * @param <T> 数据类型
 * @param <VH> ViewHolder类型
 */
public abstract class BaseAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    protected Context context;
    protected List<T> dataList;

    public BaseAdapter(Context context) {
        this.context = context;
        this.dataList = new ArrayList<>();
    }

    public BaseAdapter(Context context, List<T> dataList) {
        this.context = context;
        this.dataList = dataList != null ? dataList : new ArrayList<>();
    }

    /**
     * 更新数据列表，使用DiffUtil进行高效更新
     * @param newDataList 新的数据列表
     * @param itemCallback 比较回调
     */
    public void updateData(List<T> newDataList, DiffUtilCallback.ItemCallback<T> itemCallback) {
        if (newDataList == null) {
            newDataList = new ArrayList<>();
        }

        // 调用方把自己的 List 传进构造器时，dataList 与它是同一个对象；
        // 这种情况下 DiffUtil 算不出任何变化，只能全量刷新兜底
        if (this.dataList == newDataList) {
            notifyDataSetChanged();
            return;
        }

        DiffUtilCallback<T> diffCallback = new DiffUtilCallback<>(this.dataList, newDataList, itemCallback);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        this.dataList = newDataList;
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * 直接设置数据列表（不使用DiffUtil）
     * @param dataList 新的数据列表
     */
    public void setDataList(List<T> dataList) {
        this.dataList = dataList != null ? dataList : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * 添加单个数据项
     * @param item 数据项
     */
    public void addItem(T item) {
        if (item != null) {
            dataList.add(item);
            notifyItemInserted(dataList.size() - 1);
        }
    }

    /**
     * 添加数据列表
     * @param items 数据列表
     */
    public void addItems(List<T> items) {
        if (items != null && !items.isEmpty()) {
            int startPosition = dataList.size();
            dataList.addAll(items);
            notifyItemRangeInserted(startPosition, items.size());
        }
    }

    /**
     * 移除单个数据项
     * @param position 位置
     */
    public void removeItem(int position) {
        if (position >= 0 && position < dataList.size()) {
            dataList.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * 清空数据列表
     */
    public void clearData() {
        dataList.clear();
        notifyDataSetChanged();
    }

    /**
     * 获取数据列表
     * @return 数据列表
     */
    public List<T> getDataList() {
        return dataList;
    }

    /**
     * 获取指定位置的数据项
     * @param position 位置
     * @return 数据项
     */
    public T getItem(int position) {
        if (position >= 0 && position < dataList.size()) {
            return dataList.get(position);
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    /**
     * 加载布局文件
     * @param layoutResId 布局资源ID
     * @param parent 父视图
     * @return 视图
     */
    protected View inflateView(int layoutResId, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(layoutResId, parent, false);
    }
}
