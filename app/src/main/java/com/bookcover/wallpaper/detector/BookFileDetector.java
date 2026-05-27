package com.bookcover.wallpaper.detector;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 书籍文件检测器
 * 通过 MediaStore 和文件系统扫描检测最近打开/修改的电子书文件
 */
public class BookFileDetector {

    private static final String TAG = "BookFileDetector";

    // 支持的电子书 MIME 类型
    private static final String[] EBOOK_MIME_TYPES = {
            "application/epub+zip",
            "application/pdf",
            "application/x-mobipocket-ebook",
            "application/vnd.amazon.mobi8-ebook",
            "application/x-mobi8-ebook",
            "text/plain",
            "application/x-txt",
    };

    // 支持的文件扩展名
    private static final String[] EBOOK_EXTENSIONS = {
            ".epub", ".pdf", ".mobi", ".azw", ".azw3", ".txt",
            ".djvu", ".fb2", ".rtf", ".doc", ".docx"
    };

    private final Context context;

    public BookFileDetector(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 查询最近修改的电子书文件
     *
     * @param limit  返回数量限制
     * @param withinSeconds 只返回最近 N 秒内修改的文件
     * @return 按修改时间降序排列的文件路径列表
     */
    public List<String> queryRecentEbooks(int limit, int withinSeconds) {
        List<String> results = new ArrayList<>();

        String[] projection = {
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.TITLE
        };

        // 构建 MIME 类型查询条件
        StringBuilder selection = new StringBuilder();
        String[] selectionArgs = new String[EBOOK_MIME_TYPES.length];
        for (int i = 0; i < EBOOK_MIME_TYPES.length; i++) {
            if (i > 0) selection.append(" OR ");
            selection.append(MediaStore.Files.FileColumns.MIME_TYPE).append(" = ?");
            selectionArgs[i] = EBOOK_MIME_TYPES[i];
        }

        // 只查询文件，排除目录
        selection.append(" AND ")
                .append(MediaStore.Files.FileColumns.MEDIA_TYPE).append(" = ?");

        String[] fullArgs = new String[selectionArgs.length + 1];
        System.arraycopy(selectionArgs, 0, fullArgs, 0, selectionArgs.length);
        fullArgs[selectionArgs.length] = String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_NONE);

        String sortOrder = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC LIMIT " + limit;

        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    selection.toString(),
                    fullArgs,
                    sortOrder
            );

            if (cursor != null) {
                long cutoffTime = (System.currentTimeMillis() / 1000) - withinSeconds;
                int pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
                int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE);

                while (cursor.moveToNext()) {
                    String path = cursor.getString(pathCol);
                    long lastModified = cursor.getLong(dateCol);
                    long fileSize = cursor.getLong(sizeCol);

                    // 过滤条件：时间范围内且文件大于 1KB
                    if (path != null && lastModified > cutoffTime && fileSize > 1024) {
                        results.add(path);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "查询 MediaStore 失败", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return results;
    }

    /**
     * 扫描指定目录下最近修改的电子书文件
     *
     * @param directory 要扫描的目录
     * @param limit     返回数量
     * @return 按修改时间降序排列的文件路径列表
     */
    public List<String> scanDirectoryForEbooks(String directory, int limit) {
        List<FileEntry> entries = new ArrayList<>();
        File dir = new File(directory);

        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }

        scanDirectoryRecursive(dir, entries, 0);

        // 按修改时间降序排序
        Collections.sort(entries, (a, b) -> Long.compare(b.lastModified, a.lastModified));

        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            results.add(entries.get(i).path);
        }
        return results;
    }

    private void scanDirectoryRecursive(File dir, List<FileEntry> entries, int depth) {
        if (depth > 5) return; // 限制递归深度

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursive(file, entries, depth + 1);
            } else if (isEbookFile(file.getName())) {
                entries.add(new FileEntry(file.getAbsolutePath(), file.lastModified()));
            }
        }
    }

    /**
     * 判断文件名是否为电子书格式
     */
    public static boolean isEbookFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        for (String ext : EBOOK_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    /**
     * 根据文件扩展名获取书籍类型
     */
    public static String getBookType(String filePath) {
        if (filePath == null) return "unknown";
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".epub")) return "epub";
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".mobi") || lower.endsWith(".azw") || lower.endsWith(".azw3")) return "mobi";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".djvu")) return "djvu";
        if (lower.endsWith(".fb2")) return "fb2";
        return "unknown";
    }

    /**
     * 从文件路径提取书名（去掉扩展名和路径）
     */
    public static String extractBookTitle(String filePath) {
        if (filePath == null) return "未知书籍";
        String name = new File(filePath).getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }
        return name;
    }

    private static class FileEntry {
        String path;
        long lastModified;

        FileEntry(String path, long lastModified) {
            this.path = path;
            this.lastModified = lastModified;
        }
    }
}
