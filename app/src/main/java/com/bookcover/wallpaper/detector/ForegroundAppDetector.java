package com.bookcover.wallpaper.detector;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 前台阅读应用检测器
 * 通过 UsageStatsManager 检测当前前台应用是否为已知的阅读器
 */
public class ForegroundAppDetector {

    private static final String TAG = "ForegroundDetector";

    // 已知的阅读应用包名列表
    public static final List<String> DEFAULT_READER_PACKAGES = Arrays.asList(
            // 文石 BOOX
            "com.onyx.kreader",
            "com.onyx.kreader.hd",
            "com.onyx.android.plat",
            // 掌阅 iReader
            "com.chaozh.iReaderFree",
            "com.chaozh.iReader",
            "com.zhangyue.iReader",
            // 汉王
            "com.hanvon.reader",
            "com.hanvon.hwreader",
            // 大我 Likebook / Boyue
            "com.boyue.likebook",
            "com.boyue.reader",
            // Kindle
            "com.amazon.kindle",
            // Moon+ Reader
            "com.flyersoft.moonreader",
            "com.flyersoft.moonreaderp",
            // 微信读书
            "com.tencent.weread",
            "com.tencent.weread.widget",
            // Google Play Books
            "com.google.android.apps.books",
            // Book Reader (FB2/EPUB)
            "com.foobnix.pro.pdf.reader",
            "com.foobnix.pdf.reader",
            // Alreader
            "com.alreader",
            // Cool Reader
            "org.coolreader",
            // PocketBook
            "com.pocketbook.reader",
            // KOReader
            "org.koreader.launcher.fdroid",
            // 小米多看
            "com.duokan.reader",
            // ReadEra
            "com.readera",
            // Legimi
            "pl.legimi",
            // Nook
            "bn.ereader",
            // Adobe Acrobat Reader
            "com.adobe.reader",
            // WPS
            "cn.wps.moffice_eng"
    );

    private final Context context;
    private List<String> customReaderPackages = new ArrayList<>();

    public ForegroundAppDetector(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 获取当前前台应用的包名
     *
     * @return 前台应用包名，获取失败返回 null
     */
    public String getForegroundApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.w(TAG, "UsageStatsManager requires API 22+");
            return null;
        }

        UsageStatsManager usm = (UsageStatsManager)
                context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            Log.e(TAG, "UsageStatsManager is null");
            return null;
        }

        long now = System.currentTimeMillis();
        // 查询最近 10 秒内的使用统计
        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, now - 10000, now);

        if (stats == null || stats.isEmpty()) {
            return null;
        }

        // 找到最近使用的应用
        SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
        for (UsageStats us : stats) {
            if (us.getLastTimeUsed() > 0) {
                sortedMap.put(us.getLastTimeUsed(), us);
            }
        }

        if (sortedMap.isEmpty()) {
            return null;
        }

        // 返回最近使用时间最晚的包名
        return sortedMap.get(sortedMap.lastKey()).getPackageName();
    }

    /**
     * 判断当前前台是否为阅读应用
     *
     * @return true 如果前台是已知的阅读器
     */
    public boolean isReadingAppForeground() {
        String pkg = getForegroundApp();
        return pkg != null && getAllReaderPackages().contains(pkg);
    }

    /**
     * 获取当前前台阅读应用的包名
     *
     * @return 阅读器包名，如果不是阅读器返回 null
     */
    public String getForegroundReadingApp() {
        String pkg = getForegroundApp();
        if (pkg != null && getAllReaderPackages().contains(pkg)) {
            return pkg;
        }
        return null;
    }

    /**
     * 获取所有阅读器包名列表（默认 + 自定义）
     */
    public List<String> getAllReaderPackages() {
        List<String> all = new ArrayList<>(DEFAULT_READER_PACKAGES);
        all.addAll(customReaderPackages);
        return all;
    }

    /**
     * 添加自定义阅读器包名
     */
    public void addCustomReaderPackage(String packageName) {
        if (packageName != null && !packageName.trim().isEmpty()
                && !customReaderPackages.contains(packageName)) {
            customReaderPackages.add(packageName);
        }
    }

    /**
     * 移除自定义阅读器包名
     */
    public void removeCustomReaderPackage(String packageName) {
        customReaderPackages.remove(packageName);
    }

    /**
     * 检查是否有 PACKAGE_USAGE_STATS 权限
     */
    public static boolean hasUsageStatsPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return false;
        }
        UsageStatsManager usm = (UsageStatsManager)
                context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return false;

        long now = System.currentTimeMillis();
        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, now - 60000, now);
        return stats != null && !stats.isEmpty();
    }
}
