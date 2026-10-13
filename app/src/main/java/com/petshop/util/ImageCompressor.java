package com.petshop.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 图片压缩工具类
 * 用于上传图片前进行压缩，减少流量消耗和上传时间
 */
public class ImageCompressor {
    private static final String TAG = "ImageCompressor";
    
    // 最大文件大小（500KB）
    private static final long MAX_FILE_SIZE = 500 * 1024;
    
    // 最大宽度/高度
    private static final int MAX_WIDTH = 1920;
    private static final int MAX_HEIGHT = 1920;
    
    // 初始压缩质量
    private static final int INITIAL_QUALITY = 85;
    
    // 最小压缩质量
    private static final int MIN_QUALITY = 30;

    // 头像边长：512px 足够清晰且上传体积很小
    public static final int AVATAR_SIZE = 512;
    private static final int AVATAR_QUALITY = 88;

    /**
     * 处理头像（从相册选择的 content:// Uri）
     * 不依赖 MediaStore.DATA 列，直接读取流，兼容 Android 10+ 的分区存储。
     */
    public static File compressAvatar(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }
        File temp = copyToCache(context, uri);
        if (temp == null) {
            return null;
        }
        File avatar = compressAvatar(context, temp);
        if (!temp.equals(avatar)) {
            temp.delete();
        }
        return avatar;
    }

    /**
     * 处理头像（文件）：校正 EXIF 旋转 -> 居中裁剪为正方形 ->缩放 512px -> 压缩保存
     */
    public static File compressAvatar(Context context, File source) {
        if (context == null || source == null || !source.exists()) {
            Log.e(TAG, "头像源文件不存在");
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.e(TAG, "头像图片解码失败");
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateInSampleSize(bounds, AVATAR_SIZE * 2, AVATAR_SIZE * 2);
            Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
            if (bitmap == null) {
                Log.e(TAG, "头像图片采样解码失败");
                return null;
            }

            bitmap = rotateByExif(source, bitmap);
            bitmap = centerCropSquare(bitmap);
            if (bitmap.getWidth() != AVATAR_SIZE || bitmap.getHeight() != AVATAR_SIZE) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, AVATAR_SIZE, AVATAR_SIZE, true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                }
                bitmap = scaled;
            }

            File output = new File(context.getCacheDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(output)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, AVATAR_QUALITY, fos);
                fos.flush();
            }
            bitmap.recycle();
            Log.d(TAG, "头像处理完成: " + output.length() / 1024 + "KB");
            return output;
        } catch (Exception e) {
            Log.e(TAG, "头像处理失败", e);
            return null;
        }
    }

    /**
     * 把 content:// Uri 复制到缓存目录，供裁剪页按文件路径读取
     */
    public static File copyToCache(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            File temp = new File(context.getCacheDir(), "picked_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8 * 1024];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
            return temp;
        } catch (Exception e) {
            Log.e(TAG, "读取相册图片失败", e);
            return null;
        }
    }

    /**
     * 裁剪页加载用：按最大边长采样解码，并按 EXIF 摆正方向
     */
    public static Bitmap decodeForCrop(File file, int maxSize) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "裁剪源文件不存在");
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateInSampleSize(bounds, maxSize, maxSize);
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) {
                return null;
            }
            return rotateByExif(file, bitmap);
        } catch (Exception e) {
            Log.e(TAG, "裁剪图片解码失败", e);
            return null;
        }
    }

    /**
     * 把裁剪结果保存为 JPEG 缓存文件，调用方负责 recycle 传入的 bitmap
     */
    public static File saveJpeg(Context context, Bitmap bitmap, int quality) {
        if (context == null || bitmap == null) {
            return null;
        }
        try {
            File output = new File(context.getCacheDir(),
                    "avatar_crop_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(output)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                fos.flush();
            }
            return output;
        } catch (Exception e) {
            Log.e(TAG, "保存裁剪结果失败", e);
            return null;
        }
    }

    private static Bitmap rotateByExif(File file, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int degrees;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degrees = 270;
                    break;
                default:
                    return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Exception e) {
            Log.w(TAG, "读取 EXIF 失败，忽略旋转: " + e.getMessage());
            return bitmap;
        }
    }

    private static Bitmap centerCropSquare(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width == height) {
            return source;
        }
        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        Bitmap cropped = Bitmap.createBitmap(source, x, y, size, size);
        if (cropped != source) {
            source.recycle();
        }
        return cropped;
    }

    /**
     * 压缩图片文件
     * @param context 上下文，用于获取缓存目录
     * @param sourceFile 源文件
     * @return 压缩后的文件路径
     */
    public static String compressImage(Context context, File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            Log.e(TAG, "源文件不存在");
            return null;
        }
        
        try {
            long originalSize = sourceFile.length();
            Log.d(TAG, "原始文件大小: " + formatFileSize(originalSize));
            
            // 如果文件已经小于最大限制，直接返回
            if (originalSize <= MAX_FILE_SIZE) {
                Log.d(TAG, "文件已小于限制，无需压缩");
                return sourceFile.getAbsolutePath();
            }
            
            // 解码图片获取尺寸
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), options);
            
            // 计算采样率
            options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT);
            options.inJustDecodeBounds = false;
            
            // 解码图片
            Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), options);
            if (bitmap == null) {
                Log.e(TAG, "图片解码失败");
                return null;
            }
            
            // 使用应用缓存目录保存压缩后的图片（避免系统临时目录权限问题）
            File cacheDir = context.getCacheDir();
            File compressedFile = new File(cacheDir, 
                    "compressed_" + System.currentTimeMillis() + ".jpg");
            Log.d(TAG, "压缩文件输出路径: " + compressedFile.getAbsolutePath());
            
            compressBitmap(bitmap, compressedFile);
            
            // 回收Bitmap
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            
            long compressedSize = compressedFile.length();
            Log.d(TAG, "压缩后大小: " + formatFileSize(compressedSize) + 
                    " (压缩率: " + String.format("%.2f", (1 - compressedSize * 1.0 / originalSize) * 100) + "%)");
            
            return compressedFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "压缩失败", e);
            return sourceFile.getAbsolutePath(); // 失败时返回原文件
        }
    }

    /**
     * 压缩Bitmap到文件
     */
    private static void compressBitmap(Bitmap bitmap, File outputFile) throws IOException {
        int quality = INITIAL_QUALITY;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // 先尝试用初始质量压缩
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        
        // 如果仍然太大，逐步降低质量
        while (baos.toByteArray().length > MAX_FILE_SIZE && quality > MIN_QUALITY) {
            baos.reset();
            quality -= 5;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            Log.d(TAG, "降低质量到: " + quality);
        }
        
        // 写入文件
        FileOutputStream fos = new FileOutputStream(outputFile);
        fos.write(baos.toByteArray());
        fos.flush();
        fos.close();
        baos.close();
    }

    /**
     * 计算采样率
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, 
                                              int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && 
                   (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * 格式化文件大小
     */
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        }
    }
}
