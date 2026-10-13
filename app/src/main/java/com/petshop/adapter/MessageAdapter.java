package com.petshop.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.petshop.R;
import com.petshop.model.Message;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private Context context;
    private List<Message> messageList;

    public MessageAdapter(Context context, List<Message> messageList) {
        this.context = context;
        this.messageList = messageList;
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        Message message = messageList.get(position);

        if (message.getType() == Message.TYPE_USER) {
            // 用户消息
            holder.llUserMessage.setVisibility(View.VISIBLE);
            holder.llAIMessage.setVisibility(View.GONE);
            holder.tvUserMessage.setText(message.getContent());
        } else {
            // AI消息
            holder.llUserMessage.setVisibility(View.GONE);
            holder.llAIMessage.setVisibility(View.VISIBLE);
            holder.tvAIMessage.setText(message.getContent());
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout llUserMessage, llAIMessage;
        TextView tvUserMessage, tvAIMessage;

        public MessageViewHolder(View itemView) {
            super(itemView);
            llUserMessage = itemView.findViewById(R.id.ll_user_message);
            llAIMessage = itemView.findViewById(R.id.ll_ai_message);
            tvUserMessage = itemView.findViewById(R.id.tv_user_message);
            tvAIMessage = itemView.findViewById(R.id.tv_ai_message);
        }
        
    }
}


