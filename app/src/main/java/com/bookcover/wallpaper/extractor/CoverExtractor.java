package com.bookcover.wallpaper.extractor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 封面提取统一入口
 * 根据文件类型自动选择对应的提取器
 */
public class CoverExtractor {

    private static final String TAG = "CoverExtractor";

    /**
     * 从电子书文件中提取封面
     *
     * @param filePath    电子书文件路径
     * @param targetWidth  目标宽度（像素）
     * @param targetHeight 目标高度（像素）
     * @param grayscale    是否转换为灰度（电纸书优化）
     * @return 封面 Bitmap，失败返回 null
     */
    public static Bitmap extract(String filePath, int targetWidth, int targetHeight, boolean grayscale) {
        if (filePath == null || !new File(filePath).exists()) {
            Log.w(TAG, "文件不存在: " + filePath);
            return null;
        }

        String lowerPath = filePath.toLowerCase();
        Bitmap cover = null;

        try {
            if (lowerPath.endsWith(".epub")) {
                cover = EpubCoverExtractor.extract(filePath);
            } else if (lowerPath.endsWith(".pdf")) {
                cover = PdfCoverExtractor.extract(filePath, targetWidth, targetHeight);
            } else if (lowerPath.endsWith(".mobi") || lowerPath.endsWith(".azw") || lowerPath.endsWith(".azw3")) {
                // MOBI 格式较复杂，生成文字封面
                String title = BookFileDetectorHelper.extractBookTitle(filePath);
                cover = TextCoverGenerator.generate(title, null, targetWidth, targetHeight);
            } else if (lowerPath.endsWith(".txt")) {
                String title = BookFileDetectorHelper.extractBookTitle(filePath);
                cover = TextCoverGenerator.generate(title, null, targetWidth, targetHeight);
            } else if (lowerPath.endsWith(".fb2")) {
                cover = Fb2CoverExtractor.extract(filePath);
            } else {
                String title = BookFileDetectorHelper.extractBookTitle(filePath);
                cover = TextCoverGenerator.generate(title, null, targetWidth, targetHeight);
            }
        } catch (Exception e) {
            Log.e(TAG, "提取封面失败: " + filePath, e);
        }

        if (cover == null) {
            // 所有提取方式失败，生成文字封面作为兜底
            String title = BookFileDetectorHelper.extractBookTitle(filePath);
            cover = TextCoverGenerator.generate(title, null, targetWidth, targetHeight);
        }

        if (cover != null) {
            // 缩放到目标尺寸
            cover = scaleBitmap(cover, targetWidth, targetHeight);

            // 灰度转换
            if (grayscale) {
                cover = toGrayscale(cover);
            }
        }

        return cover;
    }

    /**
     * 缩放 Bitmap 到目标尺寸（保持比例，居中裁切）
     */
    public static Bitmap scaleBitmap(Bitmap source, int targetWidth, int targetHeight) {
        if (source == null) return null;
        if (source.getWidth() == targetWidth && source.getHeight() == targetHeight) {
            return source;
        }

        float srcRatio = (float) source.getWidth() / source.getHeight();
        float targetRatio = (float) targetWidth / targetHeight;

        float scale;
        if (srcRatio > targetRatio) {
            scale = (float) targetHeight / source.getHeight();
        } else {
            scale = (float) targetWidth / source.getWidth();
        }

        int scaledW = (int) (source.getWidth() * scale);
        int scaledH = (int) (source.getHeight() * scale);

        Bitmap scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true);

        // 居中裁切
        int x = (scaledW - targetWidth) / 2;
        int y = (scaledH - targetHeight) / 2;
        x = Math.max(0, x);
        y = Math.max(0, y);

        int cropW = Math.min(targetWidth, scaledW);
        int cropH = Math.min(targetHeight, scaledH);

        if (x == 0 && y == 0 && cropW == scaledW && cropH == scaledH) {
            return scaled;
        }

        Bitmap cropped = Bitmap.createBitmap(scaled, x, y, cropW, cropH);
        if (cropped != scaled) {
            scaled.recycle();
        }
        return cropped;
    }

    /**
     * 转换为灰度图（电纸书优化）
     */
    public static Bitmap toGrayscale(Bitmap source) {
        if (source == null) return null;
        int w = source.getWidth();
        int h = source.getHeight();

        Bitmap grayBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayBitmap);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(source, 0, 0, paint);

        return grayBitmap;
    }

    /**
     * 保存 Bitmap 到文件
     */
    public static boolean saveToFile(Bitmap bitmap, String filePath) {
        if (bitmap == null || filePath == null) return false;
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "保存封面失败", e);
            return false;
        }
    }

    /**
     * 从文件加载 Bitmap
     */
    public static Bitmap loadFromFile(String filePath) {
        if (filePath == null || !new File(filePath).exists()) return null;
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return BitmapFactory.decodeStream(fis);
        } catch (IOException e) {
            Log.e(TAG, "加载封面失败", e);
            return null;
        }
    }

    // 简单的辅助类，避免循环依赖
    private static class BookFileDetectorHelper {
        static String extractBookTitle(String filePath) {
            if (filePath == null) return "未知书籍";
            String name = new File(filePath).getName();
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) name = name.substring(0, dotIndex);
            return name;
        }
    }
}
