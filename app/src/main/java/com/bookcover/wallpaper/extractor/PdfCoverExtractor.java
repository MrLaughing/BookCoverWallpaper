package com.bookcover.wallpaper.extractor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * PDF 封面提取器
 * 使用 Android 原生 PdfRenderer 提取第一页作为封面
 */
public class PdfCoverExtractor {

    private static final String TAG = "PdfCover";

    /**
     * 从 PDF 文件提取第一页作为封面
     *
     * @param pdfPath      PDF 文件路径
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @return 封面 Bitmap，失败返回 null
     */
    public static Bitmap extract(String pdfPath, int targetWidth, int targetHeight) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.w(TAG, "PdfRenderer requires API 21+");
            return null;
        }

        File file = new File(pdfPath);
        if (!file.exists()) {
            Log.w(TAG, "PDF 文件不存在: " + pdfPath);
            return null;
        }

        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_ONLY)) {

            if (fd == null) return null;

            PdfRenderer renderer = new PdfRenderer(fd);
            if (renderer.getPageCount() == 0) {
                renderer.close();
                return null;
            }

            PdfRenderer.Page page = renderer.openPage(0);

            // 计算缩放比例
            float pageRatio = (float) page.getWidth() / page.getHeight();
            float targetRatio = (float) targetWidth / targetHeight;

            int renderWidth, renderHeight;
            if (pageRatio > targetRatio) {
                renderWidth = targetWidth;
                renderHeight = (int) (targetWidth / pageRatio);
            } else {
                renderHeight = targetHeight;
                renderWidth = (int) (targetHeight * pageRatio);
            }

            // 创建白色背景的 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            // 渲染 PDF 页面到临时 Bitmap
            Bitmap pageBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
            page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            // 居中绘制
            int left = (targetWidth - renderWidth) / 2;
            int top = (targetHeight - renderHeight) / 2;
            canvas.drawBitmap(pageBitmap, left, top, null);

            pageBitmap.recycle();
            page.close();
            renderer.close();

            return bitmap;

        } catch (IOException e) {
            Log.e(TAG, "PDF 封面提取失败: " + e.getMessage());
        } catch (SecurityException e) {
            Log.e(TAG, "无权限读取 PDF: " + e.getMessage());
        }

        return null;
    }
}
