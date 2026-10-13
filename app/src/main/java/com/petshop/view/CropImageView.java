package com.petshop.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 头像裁剪视图：单指拖动 + 双指缩放，裁剪框为居中正方形并以圆形遮罩呈现。
 *
 * 坐标约定：视图坐标 = displayMatrix * 图片坐标，displayMatrix 只包含缩放与平移，
 * 因此可以用逆矩阵把裁剪框精确映射回原图区域。
 */
public class CropImageView extends AppCompatImageView {

    /** 相对「铺满裁剪框」的最大放大倍数 */
    private static final float MAX_ZOOM = 4f;
    /** 裁剪框边长占视图短边的比例 */
    private static final float CROP_RATIO = 0.78f;

    private final Matrix displayMatrix = new Matrix();
    private final RectF cropRect = new RectF();
    private final Path maskPath = new Path();
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] matrixValues = new float[9];

    private Bitmap sourceBitmap;
    private float minScale = 1f;
    private float maxScale = 1f;

    private float lastX, lastY, lastSpan;

    public CropImageView(Context context) {
        this(context, null);
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.MATRIX);
        maskPaint.setColor(0xB3000000);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        ringPaint.setColor(0xFFFFFFFF);
    }

    /**
     * 设置待裁剪图片（已按 EXIF 校正并限制分辨率），会自动铺满裁剪框并居中
     */
    public void setCropBitmap(Bitmap bitmap) {
        sourceBitmap = bitmap;
        setImageBitmap(bitmap);
        if (getWidth() > 0 && getHeight() > 0) {
            resetMatrix();
        } else {
            // 视图尚未完成布局时，等下一帧拿到尺寸后再铺满裁剪框
            post(() -> {
                if (getWidth() > 0 && getHeight() > 0) {
                    resetMatrix();
                }
            });
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetMatrix();
    }

    private void updateCropRect() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        float side = Math.min(w, h) * CROP_RATIO;
        float cx = w / 2f;
        float cy = h / 2f;
        cropRect.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f);
    }

    private void resetMatrix() {
        if (sourceBitmap == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        updateCropRect();
        float side = cropRect.width();
        float bmpW = sourceBitmap.getWidth();
        float bmpH = sourceBitmap.getHeight();
        if (side <= 0 || bmpW <= 0 || bmpH <= 0) {
            return;
        }
        // 取 max 保证短边也能铺满裁剪框
        float baseScale = Math.max(side / bmpW, side / bmpH);
        minScale = baseScale;
        maxScale = baseScale * MAX_ZOOM;

        float tx = cropRect.centerX() - bmpW * baseScale / 2f;
        float ty = cropRect.centerY() - bmpH * baseScale / 2f;
        displayMatrix.setScale(baseScale, baseScale);
        displayMatrix.postTranslate(tx, ty);
        setImageMatrix(displayMatrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (sourceBitmap == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                lastSpan = 0;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                lastSpan = spacing(event);
                lastX = (event.getX(0) + event.getX(1)) / 2f;
                lastY = (event.getY(0) + event.getY(1)) / 2f;
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() >= 2) {
                    float span = spacing(event);
                    if (lastSpan > 10 && span > 10) {
                        float factor = span / lastSpan;
                        float cx = (event.getX(0) + event.getX(1)) / 2f;
                        float cy = (event.getY(0) + event.getY(1)) / 2f;
                        displayMatrix.postScale(factor, factor, cx, cy);
                        clampMatrix();
                    }
                    lastSpan = span;
                } else {
                    float x = event.getX();
                    float y = event.getY();
                    displayMatrix.postTranslate(x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;
                    clampMatrix();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastSpan = 0;
                break;
            default:
                break;
        }
        return true;
    }

    private float spacing(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0;
        }
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 约束缩放范围，并保证图片始终覆盖裁剪框，避免出现黑边
     */
    private void clampMatrix() {
        if (sourceBitmap == null || cropRect.isEmpty()) {
            return;
        }
        displayMatrix.getValues(matrixValues);
        float scale = matrixValues[Matrix.MSCALE_X];

        if (scale < minScale || scale > maxScale) {
            float target = Math.max(minScale, Math.min(maxScale, scale));
            float factor = target / scale;
            displayMatrix.postScale(factor, factor, cropRect.centerX(), cropRect.centerY());
            displayMatrix.getValues(matrixValues);
            scale = target;
        }

        float imgW = sourceBitmap.getWidth() * scale;
        float imgH = sourceBitmap.getHeight() * scale;
        float tx = matrixValues[Matrix.MTRANS_X];
        float ty = matrixValues[Matrix.MTRANS_Y];

        matrixValues[Matrix.MTRANS_X] = clampOffset(tx, imgW, cropRect.width(), cropRect.centerX());
        matrixValues[Matrix.MTRANS_Y] = clampOffset(ty, imgH, cropRect.height(), cropRect.centerY());
        displayMatrix.setValues(matrixValues);
        setImageMatrix(displayMatrix);
    }

    private float clampOffset(float trans, float imgSize, float cropSize, float cropCenter) {
        // 最左：图片右边界贴裁剪框右边；最右：图片左边界贴裁剪框左边
        float min = cropCenter + cropSize / 2f - imgSize;
        float max = cropCenter - cropSize / 2f;
        if (min > max) {
            return min;
        }
        return Math.max(min, Math.min(max, trans));
    }

    /**
     * 按当前变换裁剪出正方形图片
     *
     * @param outputSize 输出图片边长（px）
     * @return 裁剪结果，失败返回 null
     */
    public Bitmap crop(int outputSize) {
        if (sourceBitmap == null || cropRect.isEmpty() || outputSize <= 0) {
            return null;
        }
        Matrix inverse = new Matrix();
        if (!displayMatrix.invert(inverse)) {
            return null;
        }
        RectF srcRect = new RectF(cropRect);
        inverse.mapRect(srcRect);
        srcRect.intersect(0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight());
        if (srcRect.width() < 1 || srcRect.height() < 1) {
            return null;
        }
        int side = Math.round(Math.min(srcRect.width(), srcRect.height()));
        int x = Math.round(srcRect.centerX() - side / 2f);
        int y = Math.round(srcRect.centerY() - side / 2f);
        x = Math.max(0, Math.min(x, sourceBitmap.getWidth() - side));
        y = Math.max(0, Math.min(y, sourceBitmap.getHeight() - side));
        if (side <= 0) {
            return null;
        }

        Bitmap cropped = Bitmap.createBitmap(sourceBitmap, x, y, side, side);
        if (side != outputSize) {
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true);
            if (scaled != cropped) {
                cropped.recycle();
            }
            cropped = scaled;
        }
        return cropped;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cropRect.isEmpty()) {
            return;
        }
        float cx = cropRect.centerX();
        float cy = cropRect.centerY();
        float radius = cropRect.width() / 2f;

        // 圆形以外的区域压暗，中间保持原图亮度
        maskPath.reset();
        maskPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        maskPath.addCircle(cx, cy, radius, Path.Direction.CW);
        maskPath.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(maskPath, maskPaint);

        canvas.drawCircle(cx, cy, radius, ringPaint);
    }
}
