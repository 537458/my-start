package com.petshop.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 大图查看控件：支持双指缩放、双击放大与拖动。
 * 以 fitCenter 的缩放比作为基准（即最小缩放），拖动范围限制在图片可见区域内。
 */
public class ZoomImageView extends AppCompatImageView {

    /** 最大放大倍数（相对整图适配状态） */
    private static final float MAX_ZOOM = 4f;
    private static final float DOUBLE_TAP_ZOOM = 2f;
    private static final float ZOOM_EPSILON = 0.01f;

    private final Matrix drawMatrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private float imageWidth;
    private float imageHeight;
    /** 整图适配屏幕时的缩放比，同时作为最小缩放 */
    private float baseScale = 1f;
    private float scale = 1f;
    private float translateX;
    private float translateY;
    private float lastX;
    private float lastY;

    private OnTapListener tapListener;

    public interface OnTapListener {
        /** zoomed=true 表示点击时处于放大状态 */
        void onTap(boolean zoomed);
    }

    public ZoomImageView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        // SimpleOnGestureListener 已实现 OnDoubleTapListener，构造时即注册双击回调
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        // 缩放统一由内部矩阵接管，外部设置一律忽略
        super.setScaleType(ScaleType.MATRIX);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        // 图片尺寸要等测量完成后才知道，这里延后一帧再计算基准矩阵
        post(this::resetZoom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetZoom();
    }

    /** 还原成整图居中显示 */
    public void resetZoom() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            return;
        }
        imageWidth = intrinsicWidth;
        imageHeight = intrinsicHeight;
        baseScale = Math.min((float) getWidth() / imageWidth, (float) getHeight() / imageHeight);
        scale = baseScale;
        translateX = (getWidth() - imageWidth * scale) / 2f;
        translateY = (getHeight() - imageHeight * scale) / 2f;
        applyMatrix();
    }

    public boolean isZoomed() {
        return scale > baseScale + ZOOM_EPSILON;
    }

    private void applyMatrix() {
        drawMatrix.reset();
        drawMatrix.postScale(scale, scale);
        drawMatrix.postTranslate(translateX, translateY);
        setImageMatrix(drawMatrix);
    }

    private void clampTranslation() {
        float displayedWidth = imageWidth * scale;
        float displayedHeight = imageHeight * scale;
        if (displayedWidth <= getWidth()) {
            translateX = (getWidth() - displayedWidth) / 2f;
        } else {
            translateX = Math.min(0f, Math.max(getWidth() - displayedWidth, translateX));
        }
        if (displayedHeight <= getHeight()) {
            translateY = (getHeight() - displayedHeight) / 2f;
        } else {
            translateY = Math.min(0f, Math.max(getHeight() - displayedHeight, translateY));
        }
    }

    /** 以 (focusX, focusY) 为锚点缩放，锚点下的像素保持不动 */
    private void scaleBy(float factor, float focusX, float focusY) {
        float target = scale * factor;
        float max = baseScale * MAX_ZOOM;
        if (target < baseScale) {
            target = baseScale;
        }
        if (target > max) {
            target = max;
        }
        float actual = target / scale;
        if (Math.abs(actual - 1f) < ZOOM_EPSILON) {
            return;
        }
        translateX = focusX - (focusX - translateX) * actual;
        translateY = focusY - (focusY - translateY) * actual;
        scale = target;
        clampTranslation();
        applyMatrix();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    if (isZoomed()) {
                        translateX += event.getX() - lastX;
                        translateY += event.getY() - lastY;
                        clampTranslation();
                        applyMatrix();
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
            default:
                break;
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            boolean zoomed = isZoomed();
            if (zoomed) {
                // 放大状态下单击先还原，再由页面决定是否退出
                resetZoom();
            }
            if (tapListener != null) {
                tapListener.onTap(zoomed);
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (isZoomed()) {
                resetZoom();
            } else {
                scaleBy(DOUBLE_TAP_ZOOM, e.getX(), e.getY());
            }
            return true;
        }
    }
}
