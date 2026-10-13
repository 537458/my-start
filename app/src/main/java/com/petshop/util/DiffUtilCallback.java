package com.petshop.util;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

/**
 * 通用的 DiffUtil.Callback 实现
 * 用于优化 RecyclerView 列表更新性能
 */
public class DiffUtilCallback<T> extends DiffUtil.Callback {

    private List<T> oldList;
    private List<T> newList;
    private ItemCallback<T> itemCallback;

    public DiffUtilCallback(List<T> oldList, List<T> newList, ItemCallback<T> itemCallback) {
        this.oldList = oldList;
        this.newList = newList;
        this.itemCallback = itemCallback;
    }

    @Override
    public int getOldListSize() {
        return oldList != null ? oldList.size() : 0;
    }

    @Override
    public int getNewListSize() {
        return newList != null ? newList.size() : 0;
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        if (itemCallback == null) {
            return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
        }
        return itemCallback.areItemsTheSame(
                oldList.get(oldItemPosition),
                newList.get(newItemPosition)
        );
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        if (itemCallback == null) {
            return true;
        }
        return itemCallback.areContentsTheSame(
                oldList.get(oldItemPosition),
                newList.get(newItemPosition)
        );
    }

    @Nullable
    @Override
    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
        if (itemCallback == null) {
            return null;
        }
        return itemCallback.getChangePayload(
                oldList.get(oldItemPosition),
                newList.get(newItemPosition)
        );
    }

    /**
     * 列表项回调接口
     */
    public interface ItemCallback<T> {
        /**
         * 判断两个 item 是否是同一个对象（通常比较 ID）
         */
        boolean areItemsTheSame(T oldItem, T newItem);

        /**
         * 判断两个 item 的内容是否相同
         */
        boolean areContentsTheSame(T oldItem, T newItem);

        /**
         * 获取变化的 payload（可选）
         */
        default Object getChangePayload(T oldItem, T newItem) {
            return null;
        }
    }
}
