package com.petshop.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import java.util.List;

import com.petshop.R;
import com.petshop.model.Address;
import com.petshop.util.DiffUtilCallback;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.AddressViewHolder> {

    private Context context;
    private List<Address> addressList;
    private OnAddressClickListener onAddressClickListener;
    private int selectedAddressId = -1;

    public interface OnAddressClickListener {
        void onAddressClick(Address address);
        void onEditClick(Address address);
        void onDeleteClick(Address address);
    }

    public AddressAdapter(Context context, List<Address> addressList, OnAddressClickListener onAddressClickListener) {
        this.context = context;
        this.addressList = addressList;
        this.onAddressClickListener = onAddressClickListener;
    }

    public void updateData(List<Address> newAddressList) {
        DiffUtilCallback<Address> diffCallback = new DiffUtilCallback<>(
                this.addressList,
                newAddressList,
                new DiffUtilCallback.ItemCallback<Address>() {
                    @Override
                    public boolean areItemsTheSame(Address oldItem, Address newItem) {
                        return oldItem.getId() == newItem.getId();
                    }

                    @Override
                    public boolean areContentsTheSame(Address oldItem, Address newItem) {
                        return oldItem.getName().equals(newItem.getName()) &&
                                oldItem.getPhone().equals(newItem.getPhone()) &&
                                oldItem.getAddress().equals(newItem.getAddress()) &&
                                oldItem.isDefault() == newItem.isDefault();
                    }
                }
        );

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        this.addressList = newAddressList;
        diffResult.dispatchUpdatesTo(this);
    }

    public void setSelectedAddressId(int selectedAddressId) {
        this.selectedAddressId = selectedAddressId;
        notifyDataSetChanged();
    }

    @Override
    public AddressViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_address, parent, false);
        return new AddressViewHolder(view);
    }

    @Override
    public void onBindViewHolder(AddressViewHolder holder, int position) {
        Address address = addressList.get(position);
        holder.tvNamePhone.setText(address.getName() + " " + address.getPhone());
        holder.tvAddress.setText(address.getAddress());

        // 显示默认标记
        if (address.isDefault()) {
            holder.tvDefault.setVisibility(View.VISIBLE);
        } else {
            holder.tvDefault.setVisibility(View.GONE);
        }

        // 高亮选中的地址
        if (selectedAddressId != -1 && address.getId() == selectedAddressId) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(android.R.color.holo_orange_light));
        } else {
            holder.itemView.setBackgroundColor(context.getResources().getColor(android.R.color.white));
        }

        // 点击事件
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onAddressClickListener != null) {
                    onAddressClickListener.onAddressClick(address);
                }
            }
        });

        // 编辑按钮点击事件
        holder.tvEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onAddressClickListener != null) {
                    onAddressClickListener.onEditClick(address);
                }
            }
        });

        // 删除按钮点击事件
        holder.tvDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onAddressClickListener != null) {
                    onAddressClickListener.onDeleteClick(address);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return addressList.size();
    }

    static class AddressViewHolder extends RecyclerView.ViewHolder {
        TextView tvNamePhone;
        TextView tvAddress;
        TextView tvDefault;
        TextView tvEdit;
        TextView tvDelete;

        public AddressViewHolder(View itemView) {
            super(itemView);
            tvNamePhone = itemView.findViewById(R.id.tv_name_phone);
            tvAddress = itemView.findViewById(R.id.tv_address);
            tvDefault = itemView.findViewById(R.id.tv_default);
            tvEdit = itemView.findViewById(R.id.tv_edit);
            tvDelete = itemView.findViewById(R.id.tv_delete);
        }
    }
}