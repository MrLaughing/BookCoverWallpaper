package com.bookcover.wallpaper.extractor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * FB2 格式封面提取器
 * FB2 是 FictionBook 2.0 格式，封面以 base64 编码嵌入 XML
 */
public class Fb2CoverExtractor {

    private static final String TAG = "Fb2Cover";

    /**
     * 从 FB2 文件提取封面
     */
    public static Bitmap extract(String fb2Path) {
        try (FileInputStream fis = new FileInputStream(fb2Path)) {
            byte[] data = readAllBytes(fis);
            String content = new String(data, "UTF-8");

            // 查找 <coverpage> 或 <binary> 标签
            String coverData = extractCoverFromXml(content);
            if (coverData != null) {
                byte[] imageBytes = android.util.Base64.decode(coverData, android.util.Base64.DEFAULT);
                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            }

            // 查找第一个 <binary> 中的图片
            String binaryData = extractFirstBinary(content);
            if (binaryData != null) {
                byte[] imageBytes = android.util.Base64.decode(binaryData, android.util.Base64.DEFAULT);
                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            }

        } catch (IOException e) {
            Log.e(TAG, "FB2 封面提取失败: " + e.getMessage());
        }
        return null;
    }

    private static String extractCoverFromXml(String xml) {
        // 查找 <coverpage>
        int coverStart = xml.indexOf("<coverpage>");
        if (coverStart < 0) return null;

        int coverEnd = xml.indexOf("</coverpage>", coverStart);
        if (coverEnd < 0) return null;

        String coverSection = xml.substring(coverStart, coverEnd);

        // 在 coverpage 中找 image href="#cover-image-id"
        int hrefStart = coverSection.indexOf("href=\"#");
        if (hrefStart < 0) return null;

        int idStart = hrefStart + 7;
        int idEnd = coverSection.indexOf("\"", idStart);
        if (idEnd < 0) return null;

        String coverId = coverSection.substring(idStart, idEnd);

        // 在 <binary> 中找 id 匹配的图片
        return findBinaryById(xml, coverId);
    }

    private static String findBinaryById(String xml, String id) {
        String searchPattern = "id=\"" + id + "\"";
        int idx = xml.indexOf(searchPattern);
        if (idx < 0) return null;

        // 找到 <binary 标签内容
        int binaryStart = xml.lastIndexOf("<binary", idx);
        if (binaryStart < 0 || idx - binaryStart > 100) return null;

        int contentStart = xml.indexOf(">", binaryStart);
        if (contentStart < 0) return null;
        contentStart++;

        int contentEnd = xml.indexOf("</binary>", contentStart);
        if (contentEnd < 0) return null;

        return xml.substring(contentStart, contentEnd).trim();
    }

    private static String extractFirstBinary(String xml) {
        int binaryStart = xml.indexOf("<binary");
        if (binaryStart < 0) return null;

        int contentStart = xml.indexOf(">", binaryStart);
        if (contentStart < 0) return null;
        contentStart++;

        int contentEnd = xml.indexOf("</binary>", contentStart);
        if (contentEnd < 0) return null;

        return xml.substring(contentStart, contentEnd).trim();
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }
}
