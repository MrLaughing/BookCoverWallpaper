package com.bookcover.wallpaper.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 应用配置管理
 * 管理用户自定义的阅读器包名、书籍扫描目录等设置
 */
public class PrefsManager {

    private static final String TAG = "PrefsManager";
    private static final String PREFS_NAME = "bookcover_wallpaper_prefs";

    // 配置键
    private static final String KEY_ENABLED = "service_enabled";
    private static final String KEY_CUSTOM_READERS = "custom_reader_packages";
    private static final String KEY_SCAN_DIRS = "scan_directories";
    private static final String KEY_CHECK_INTERVAL = "check_interval_seconds";
    private static final String KEY_GRAYSCALE = "grayscale_cover";
    private static final String KEY_LAST_BOOK_PATH = "last_book_path";
    private static final String KEY_LAST_COVER_PATH = "last_cover_path";
    private static final String KEY_SHOW_BOOK_INFO = "show_book_info_on_cover";

    // 默认值
    private static final int DEFAULT_CHECK_INTERVAL = 5; // 秒
    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_GRAYSCALE = true;
    private static final boolean DEFAULT_SHOW_BOOK_INFO = true;

    // 默认扫描目录
    private static final String[] DEFAULT_SCAN_DIRS = {
            "/sdcard/Books",
            "/sdcard/Download",
            "/sdcard/Ebooks",
            "/sdcard/eBooks",
            "/sdcard/My Books",
            "/sdcard/koreader",
            "/sdcard/1/mobi",
            "/sdcard/Android/data/com.amazon.kindle/files",
            "/storage/emulated/0/Books",
            "/storage/emulated/0/Download"
    };

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---- 服务开关 ----

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    // ---- 检查间隔 ----

    public int getCheckInterval() {
        return prefs.getInt(KEY_CHECK_INTERVAL, DEFAULT_CHECK_INTERVAL);
    }

    public void setCheckInterval(int seconds) {
        prefs.edit().putInt(KEY_CHECK_INTERVAL, Math.max(1, seconds)).apply();
    }

    // ---- 灰度模式 ----

    public boolean isGrayscaleEnabled() {
        return prefs.getBoolean(KEY_GRAYSCALE, DEFAULT_GRAYSCALE);
    }

    public void setGrayscaleEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GRAYSCALE, enabled).apply();
    }

    // ---- 封面上显示书籍信息 ----

    public boolean isShowBookInfoEnabled() {
        return prefs.getBoolean(KEY_SHOW_BOOK_INFO, DEFAULT_SHOW_BOOK_INFO);
    }

    public void setShowBookInfoEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHOW_BOOK_INFO, enabled).apply();
    }

    // ---- 自定义阅读器包名 ----

    public List<String> getCustomReaderPackages() {
        Set<String> set = prefs.getStringSet(KEY_CUSTOM_READERS, Collections.emptySet());
        return new ArrayList<>(set);
    }

    public void setCustomReaderPackages(List<String> packages) {
        prefs.edit().putStringSet(KEY_CUSTOM_READERS, new HashSet<>(packages)).apply();
    }

    public void addCustomReaderPackage(String pkg) {
        List<String> list = getCustomReaderPackages();
        if (!list.contains(pkg)) {
            list.add(pkg);
            setCustomReaderPackages(list);
        }
    }

    public void removeCustomReaderPackage(String pkg) {
        List<String> list = getCustomReaderPackages();
        list.remove(pkg);
        setCustomReaderPackages(list);
    }

    // ---- 扫描目录 ----

    public List<String> getScanDirectories() {
        Set<String> saved = prefs.getStringSet(KEY_SCAN_DIRS, null);
        if (saved != null && !saved.isEmpty()) {
            return new ArrayList<>(saved);
        }
        // 返回默认目录（只返回存在的）
        List<String> dirs = new ArrayList<>();
        for (String dir : DEFAULT_SCAN_DIRS) {
            if (new File(dir).exists()) {
                dirs.add(dir);
            }
        }
        return dirs;
    }

    public void setScanDirectories(List<String> directories) {
        prefs.edit().putStringSet(KEY_SCAN_DIRS, new HashSet<>(directories)).apply();
    }

    public void addScanDirectory(String dir) {
        List<String> list = getScanDirectories();
        if (!list.contains(dir)) {
            list.add(dir);
            setScanDirectories(list);
        }
    }

    public void removeScanDirectory(String dir) {
        List<String> list = getScanDirectories();
        list.remove(dir);
        setScanDirectories(list);
    }

    // ---- 上次状态 ----

    public String getLastBookPath() {
        return prefs.getString(KEY_LAST_BOOK_PATH, null);
    }

    public void setLastBookPath(String path) {
        prefs.edit().putString(KEY_LAST_BOOK_PATH, path).apply();
    }

    public String getLastCoverPath() {
        return prefs.getString(KEY_LAST_COVER_PATH, null);
    }

    public void setLastCoverPath(String path) {
        prefs.edit().putString(KEY_LAST_COVER_PATH, path).apply();
    }
}
