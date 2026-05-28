package com.bookcover.wallpaper.extractor;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * 锁屏壁纸管理器
 * 负责将封面 Bitmap 设置为系统锁屏壁纸
 */
public class WallpaperHelper {

    private static final String TAG = "WallpaperHelper";

    private final Context context;

    public WallpaperHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 将 Bitmap 设置为锁屏壁纸
     *
     * @param bitmap 封面图片
     * @return true 设置成功
     */
    public boolean setLockScreenWallpaper(Bitmap bitmap) {
        if (bitmap == null) {
            Log.w(TAG, "封面 Bitmap 为空");
            return false;
        }

        // 先保存到缓存
        String cachePath = getCoverCachePath();
        boolean saved = CoverExtractor.saveToFile(bitmap, cachePath);
        if (!saved) {
            Log.w(TAG, "封面缓存保存失败");
        }

        try {
            WallpaperManager wm = WallpaperManager.getInstance(context);
            if (wm == null) {
                Log.e(TAG, "WallpaperManager 不可用");
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 支持仅设置锁屏壁纸
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
                Log.i(TAG, "锁屏壁纸已更新 (FLAG_LOCK)");
            } else {
                // 旧版本同时设置锁屏和主屏幕
                wm.setBitmap(bitmap);
                Log.i(TAG, "壁纸已更新 (兼容模式)");
            }
            return true;

        } catch (IOException e) {
            Log.e(TAG, "设置壁纸失败: " + e.getMessage());
            return false;
        } catch (SecurityException e) {
            Log.e(TAG, "无设置壁纸权限: " + e.getMessage());
            return false;
        }
    }

    /**
     * 从缓存文件恢复锁屏壁纸
     *
     * @return true 恢复成功
     */
    public boolean restoreFromCache() {
        String cachePath = getCoverCachePath();
        File cacheFile = new File(cachePath);
        if (!cacheFile.exists()) {
            Log.w(TAG, "封面缓存不存在");
            return false;
        }

        Bitmap bitmap = CoverExtractor.loadFromFile(cachePath);
        if (bitmap == null) {
            Log.w(TAG, "加载封面缓存失败");
            return false;
        }

        return setLockScreenWallpaper(bitmap);
    }

    /**
     * 获取封面缓存路径
     */
    public String getCoverCachePath() {
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            // 使用内部存储作为备用
            externalDir = context.getFilesDir();
        }
        File dir = new File(externalDir, "covers");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "current_cover.png").getAbsolutePath();
    }

    /**
     * 获取屏幕尺寸
     */
    public int[] getScreenSize() {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    /**
     * 检查是否有设置壁纸的权限
     */
    public static boolean hasWallpaperPermission(Context context) {
        WallpaperManager wm = WallpaperManager.getInstance(context);
        if (wm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return wm.isSetWallpaperAllowed();
        }
        return true;
    }
}
