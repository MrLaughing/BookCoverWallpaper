package com.bookcover.wallpaper.extractor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

/**
 * 文字封面生成器
 * 当无法提取书籍封面时，生成一个简洁的文字封面（类似 Kindle 效果）
 */
public class TextCoverGenerator {

    private static final int PADDING = 60; // dp
    private static final int TITLE_SIZE_SP = 28;
    private static final int AUTHOR_SIZE_SP = 16;
    private static final int SUBTITLE_SIZE_SP = 12;

    /**
     * 生成文字封面
     *
     * @param title        书名
     * @param author       作者（可为 null）
     * @param width        宽度（像素）
     * @param height       高度（像素）
     * @return 生成的封面 Bitmap
     */
    public static Bitmap generate(String title, String author, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 白色背景
        canvas.drawColor(Color.WHITE);

        // 上下装饰线
        Paint linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#333333"));
        linePaint.setStrokeWidth(2);
        float lineY1 = height * 0.15f;
        float lineY2 = height * 0.85f;
        canvas.drawLine(PADDING, lineY1, width - PADDING, lineY1, linePaint);
        canvas.drawLine(PADDING, lineY2, width - PADDING, lineY2, linePaint);

        // 书名
        TextPaint titlePaint = new TextPaint();
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(spToPx(TITLE_SIZE_SP));
        titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        titlePaint.setAntiAlias(true);

        // 自动换行
        int textWidth = width - PADDING * 2;
        StaticLayout titleLayout = new StaticLayout(
                title != null ? title : "未知书籍",
                titlePaint,
                textWidth,
                Layout.Alignment.ALIGN_CENTER,
                1.3f, // lineSpacingMultiplier
                0,    // lineSpacingAdd
                false
        );

        // 垂直居中（考虑作者名）
        float totalTextHeight = titleLayout.getHeight();
        if (author != null && !author.isEmpty()) {
            totalTextHeight += spToPx(AUTHOR_SIZE_SP) + 40;
        }

        float startY = (height - totalTextHeight) / 2f;
        canvas.save();
        canvas.translate((width - textWidth) / 2f, startY);
        titleLayout.draw(canvas);
        canvas.restore();

        // 作者
        if (author != null && !author.isEmpty()) {
            TextPaint authorPaint = new TextPaint();
            authorPaint.setColor(Color.parseColor("#666666"));
            authorPaint.setTextSize(spToPx(AUTHOR_SIZE_SP));
            authorPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            authorPaint.setAntiAlias(true);
            authorPaint.setTextAlign(Paint.Align.CENTER);

            float authorY = startY + titleLayout.getHeight() + spToPx(AUTHOR_SIZE_SP) + 20;
            canvas.drawText(author, width / 2f, authorY, authorPaint);
        }

        // 底部小字 "Book Cover"
        TextPaint subtitlePaint = new TextPaint();
        subtitlePaint.setColor(Color.parseColor("#999999"));
        subtitlePaint.setTextSize(spToPx(SUBTITLE_SIZE_SP));
        subtitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC));
        subtitlePaint.setAntiAlias(true);
        subtitlePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("— Book Cover —", width / 2f, height * 0.92f, subtitlePaint);

        return bitmap;
    }

    /**
     * 生成带书籍信息的封面（在已有封面上叠加信息）
     */
    public static Bitmap overlayInfo(Bitmap cover, String title, String author) {
        if (cover == null) return null;

        Bitmap result = cover.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(result);

        int width = result.getWidth();
        int height = result.getHeight();

        // 底部半透明背景
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#CC000000")); // 80% 黑色
        int barHeight = (int) spToPx(48);
        canvas.drawRect(0, height - barHeight, width, height, bgPaint);

        // 书名
        TextPaint infoPaint = new TextPaint();
        infoPaint.setColor(Color.WHITE);
        infoPaint.setTextSize(spToPx(14));
        infoPaint.setAntiAlias(true);
        infoPaint.setTextAlign(Paint.Align.LEFT);

        String displayText = title != null ? title : "";
        if (author != null && !author.isEmpty()) {
            displayText += " — " + author;
        }

        // 截断过长文本
        float maxTextWidth = width - 40;
        while (infoPaint.measureText(displayText) > maxTextWidth && displayText.length() > 3) {
            displayText = displayText.substring(0, displayText.length() - 4) + "...";
        }

        canvas.drawText(displayText, 20, height - barHeight / 2f + spToPx(5), infoPaint);

        return result;
    }

    private static float spToPx(float sp) {
        // 近似转换，基于 160dpi
        return sp * 2.75f;
    }
}
