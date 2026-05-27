package com.bookcover.wallpaper.extractor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUB 封面提取器
 * EPUB 本质是 ZIP 格式，通过解析 OPF 元数据找到封面图片
 */
public class EpubCoverExtractor {

    private static final String TAG = "EpubCover";

    /**
     * 从 EPUB 文件提取封面图片
     */
    public static Bitmap extract(String epubPath) {
        try {
            ZipFile zipFile = new ZipFile(epubPath);

            // 第一步：读取 container.xml 获取 OPF 路径
            String opfPath = parseContainerXml(zipFile);
            if (opfPath == null) {
                zipFile.close();
                return null;
            }

            // 第二步：解析 OPF 获取封面图片 href
            String coverHref = parseOpfForCover(zipFile, opfPath);
            if (coverHref == null) {
                zipFile.close();
                return null;
            }

            // 第三步：解析相对路径
            String coverFullPath = resolveRelativePath(opfPath, coverHref);

            // 第四步：提取图片
            ZipEntry coverEntry = zipFile.getEntry(coverFullPath);
            if (coverEntry == null) {
                // 尝试直接用 href
                coverEntry = zipFile.getEntry(coverHref);
            }
            if (coverEntry == null) {
                // 尝试 URL 编码的路径
                coverEntry = zipFile.getEntry(coverHref.replace(" ", "%20"));
            }

            if (coverEntry != null) {
                try (InputStream imgStream = zipFile.getInputStream(coverEntry)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(imgStream);
                    zipFile.close();
                    return bitmap;
                }
            }

            // 第五步：如果以上都失败，尝试在 ZIP 中找第一个大图
            Bitmap fallback = findFirstLargeImage(zipFile);
            zipFile.close();
            return fallback;

        } catch (IOException e) {
            Log.e(TAG, "EPUB 封面提取失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 解析 META-INF/container.xml 获取 OPF 文件路径
     */
    private static String parseContainerXml(ZipFile zipFile) throws IOException {
        ZipEntry containerEntry = zipFile.getEntry("META-INF/container.xml");
        if (containerEntry == null) {
            Log.w(TAG, "container.xml 不存在");
            return null;
        }

        try (InputStream is = zipFile.getInputStream(containerEntry)) {
            byte[] data = readAllBytes(is);
            String xml = new String(data, "UTF-8");

            // 简单字符串解析，避免依赖 XML 解析器
            int fullpathIdx = xml.indexOf("full-path");
            if (fullpathIdx < 0) return null;

            // 找到 full-path="..." 中的值
            int quoteStart = xml.indexOf('"', fullpathIdx);
            if (quoteStart < 0) quoteStart = xml.indexOf('\'', fullpathIdx);
            if (quoteStart < 0) return null;

            int quoteEnd = xml.indexOf(xml.charAt(quoteStart), quoteStart + 1);
            if (quoteEnd < 0) return null;

            return xml.substring(quoteStart + 1, quoteEnd);
        }
    }

    /**
     * 解析 OPF 文件获取封面图片的 href
     * 支持多种 EPUB 封面声明方式
     */
    private static String parseOpfForCover(ZipFile zipFile, String opfPath) throws IOException {
        ZipEntry opfEntry = zipFile.getEntry(opfPath);
        if (opfEntry == null) return null;

        try (InputStream is = zipFile.getInputStream(opfEntry)) {
            byte[] data = readAllBytes(is);
            String xml = new String(data, "UTF-8");

            // 方式1: <meta name="cover" content="cover-id"/>
            // 需要在 manifest 中找 id 对应的 href
            String coverId = extractMetaCover(xml);
            if (coverId != null) {
                String href = findHrefById(xml, coverId);
                if (href != null) return href;
            }

            // 方式2: <item properties="cover-image" href="cover.jpg"/>
            String coverHref = extractCoverImageProperty(xml);
            if (coverHref != null) return coverHref;

            // 方式3: <item id="cover" ... href="cover.jpg"/> 或 id="cover-image"
            for (String id : new String[]{"cover", "cover-image", "CoverImage", "book-cover"}) {
                String href = findHrefById(xml, id);
                if (href != null) return href;
            }

            // 方式4: 查找 <image> 标签（EPUB 3）
            String imageHref = extractImageHref(xml);
            if (imageHref != null) return imageHref;

            return null;
        }
    }

    /**
     * 提取 <meta name="cover" content="..."/> 中的 cover id
     */
    private static String extractMetaCover(String xml) {
        // 查找 name="cover"
        int idx = xml.indexOf("name=\"cover\"");
        if (idx < 0) idx = xml.indexOf("name='cover'");
        if (idx < 0) return null;

        // 在附近找 content="..."
        String searchArea = xml.substring(idx, Math.min(idx + 200, xml.length()));
        int contentIdx = searchArea.indexOf("content=");
        if (contentIdx < 0) return null;

        int quoteStart = searchArea.indexOf('"', contentIdx + 8);
        if (quoteStart < 0) quoteStart = searchArea.indexOf('\'', contentIdx + 8);
        if (quoteStart < 0) return null;

        int quoteEnd = searchArea.indexOf(searchArea.charAt(quoteStart), quoteStart + 1);
        if (quoteEnd < 0) return null;

        return searchArea.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * 在 manifest 中查找指定 id 对应的 href
     */
    private static String findHrefById(String xml, String id) {
        String searchPattern = "id=\"" + id + "\"";
        int idx = xml.indexOf(searchPattern);
        if (idx < 0) {
            searchPattern = "id='" + id + "'";
            idx = xml.indexOf(searchPattern);
        }
        if (idx < 0) return null;

        // 在附近找 href="..."
        String area = xml.substring(Math.max(0, idx - 200), Math.min(xml.length(), idx + 200));
        int hrefIdx = area.lastIndexOf("href=");
        if (hrefIdx < 0) return null;

        int quoteStart = area.indexOf('"', hrefIdx + 5);
        if (quoteStart < 0) quoteStart = area.indexOf('\'', hrefIdx + 5);
        if (quoteStart < 0) return null;

        int quoteEnd = area.indexOf(area.charAt(quoteStart), quoteStart + 1);
        if (quoteEnd < 0) return null;

        return area.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * 提取 properties="cover-image" 的 href
     */
    private static String extractCoverImageProperty(String xml) {
        int idx = xml.indexOf("cover-image");
        if (idx < 0) return null;

        // 回溯找 <item 标签
        int itemStart = xml.lastIndexOf("<item", idx);
        if (itemStart < 0 || idx - itemStart > 500) return null;

        String area = xml.substring(itemStart, Math.min(xml.length(), itemStart + 500));
        int hrefIdx = area.indexOf("href=");
        if (hrefIdx < 0) return null;

        int quoteStart = area.indexOf('"', hrefIdx + 5);
        if (quoteStart < 0) quoteStart = area.indexOf('\'', hrefIdx + 5);
        if (quoteStart < 0) return null;

        int quoteEnd = area.indexOf(area.charAt(quoteStart), quoteStart + 1);
        if (quoteEnd < 0) return null;

        return area.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * 提取 EPUB 3 <image> 标签的 href
     */
    private static String extractImageHref(String xml) {
        int idx = xml.indexOf("<image");
        if (idx < 0) return null;

        String area = xml.substring(idx, Math.min(xml.length(), idx + 200));
        int hrefIdx = area.indexOf("href=");
        if (hrefIdx < 0) return null;

        int quoteStart = area.indexOf('"', hrefIdx + 5);
        if (quoteStart < 0) quoteStart = area.indexOf('\'', hrefIdx + 5);
        if (quoteStart < 0) return null;

        int quoteEnd = area.indexOf(area.charAt(quoteStart), quoteStart + 1);
        if (quoteEnd < 0) return null;

        return area.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * 在 ZIP 中查找第一个较大的图片作为兜底
     */
    private static Bitmap findFirstLargeImage(ZipFile zipFile) {
        java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName().toLowerCase();
            if (entry.getSize() > 10000 && (
                    name.endsWith(".jpg") || name.endsWith(".jpeg")
                            || name.endsWith(".png") || name.endsWith(".gif"))) {
                // 排除小图标
                if (name.contains("icon") || name.contains("thumbnail")) continue;
                try (InputStream is = zipFile.getInputStream(entry)) {
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    if (bmp != null && bmp.getWidth() >= 200 && bmp.getHeight() >= 300) {
                        return bmp;
                    }
                    if (bmp != null) bmp.recycle();
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    /**
     * 解析相对路径
     */
    private static String resolveRelativePath(String basePath, String relativePath) {
        if (relativePath.startsWith("/")) return relativePath.substring(1);
        int lastSlash = basePath.lastIndexOf('/');
        if (lastSlash >= 0) {
            return basePath.substring(0, lastSlash + 1) + relativePath;
        }
        return relativePath;
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
