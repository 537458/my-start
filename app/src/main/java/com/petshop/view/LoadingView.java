package com.petshop.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.petshop.R;

public class LoadingView extends Dialog {

    private ImageView ivLoading;
    private TextView tvMessage;
    private RotateAnimation rotateAnimation;

    public LoadingView(Context context) {
        super(context);
        init(context);
    }

    public LoadingView(Context context, int themeResId) {
        super(context, themeResId);
        init(context);
    }

    private void init(Context context) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(LayoutInflater.from(context).inflate(R.layout.layout_loading, null));
        
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
            window.setLayout(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        
        ivLoading = findViewById(R.id.iv_loading);
        tvMessage = findViewById(R.id.tv_loading_message);
        
        initAnimation();
    }

    private void initAnimation() {
        rotateAnimation = new RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        );
        rotateAnimation.setDuration(1000);
        rotateAnimation.setRepeatCount(Animation.INFINITE);
        rotateAnimation.setRepeatMode(Animation.RESTART);
    }

    @Override
    public void show() {
        super.show();
        if (ivLoading != null) {
            ivLoading.startAnimation(rotateAnimation);
        }
    }

    @Override
    public void dismiss() {
        if (ivLoading != null) {
            ivLoading.clearAnimation();
        }
        super.dismiss();
    }

    public void setMessage(String message) {
        if (tvMessage != null) {
            if (message != null && !message.isEmpty()) {
                tvMessage.setText(message);
                tvMessage.setVisibility(android.view.View.VISIBLE);
            } else {
                tvMessage.setVisibility(android.view.View.GONE);
            }
        }
    }

    public static LoadingView show(Context context) {
        return show(context, null);
    }

    public static LoadingView show(Context context, String message) {
        LoadingView loadingView = new LoadingView(context);
        if (message != null && !message.isEmpty()) {
            loadingView.setMessage(message);
        }
        loadingView.show();
        return loadingView;
    }
}
