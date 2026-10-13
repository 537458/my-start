package com.petshop.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.petshop.R;

public class ToastUtil {

    private static Toast toast;

    public static void show(Context context, String message) {
        show(context, message, Toast.LENGTH_SHORT);
    }

    public static void show(Context context, String message, int duration) {
        if (context == null) return;
        
        cancel();
        
        View view = LayoutInflater.from(context).inflate(R.layout.layout_toast, null);
        TextView tvMessage = view.findViewById(R.id.tv_toast_message);
        ImageView ivIcon = view.findViewById(R.id.iv_toast_icon);
        
        tvMessage.setText(message);
        if (ivIcon != null) {
            ivIcon.setVisibility(View.GONE);
        }
        
        toast = new Toast(context);
        toast.setView(view);
        toast.setDuration(duration);
        toast.setGravity(Gravity.CENTER, 0, 100);
        
        // 添加淡入动画
        view.setAlpha(0f);
        view.animate()
                .alpha(1f)
                .setDuration(200)
                .start();
        
        toast.show();
    }

    public static void showSuccess(Context context, String message) {
        show(context, message, R.drawable.toast_success_bg, R.color.colorSuccess, R.drawable.ic_success);
    }

    public static void showError(Context context, String message) {
        show(context, message, R.drawable.toast_error_bg, R.color.colorError, R.drawable.ic_error);
    }

    public static void showWarning(Context context, String message) {
        show(context, message, R.drawable.toast_warning_bg, R.color.colorWarning, R.drawable.ic_warning);
    }

    public static void showInfo(Context context, String message) {
        show(context, message, R.drawable.toast_success_bg, R.color.colorPrimary, R.drawable.ic_success);
    }

    private static void show(Context context, String message, int bgResId, int colorResId, int iconResId) {
        if (context == null) return;
        
        cancel();
        
        View view = LayoutInflater.from(context).inflate(R.layout.layout_toast, null);
        TextView tvMessage = view.findViewById(R.id.tv_toast_message);
        ImageView ivIcon = view.findViewById(R.id.iv_toast_icon);
        
        tvMessage.setText(message);
        ivIcon.setImageResource(iconResId);
        ivIcon.setVisibility(View.VISIBLE);
        
        GradientDrawable background = (GradientDrawable) view.getBackground();
        background.setColor(context.getColor(colorResId));
        
        toast = new Toast(context);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    public static void cancel() {
        if (toast != null) {
            toast.cancel();
            toast = null;
        }
    }
}
