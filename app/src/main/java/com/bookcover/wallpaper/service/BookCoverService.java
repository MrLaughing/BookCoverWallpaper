package com.bookcover.wallpaper.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.bookcover.wallpaper.detector.BookFileDetector;
import com.bookcover.wallpaper.detector.ForegroundAppDetector;
import com.bookcover.wallpaper.extractor.CoverExtractor;
import com.bookcover.wallpaper.extractor.TextCoverGenerator;
import com.bookcover.wallpaper.extractor.WallpaperHelper;
import com.bookcover.wallpaper.ui.MainActivity;
import com.bookcover.wallpaper.util.PrefsManager;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 核心调度服务
 * 定期检测前台阅读应用，提取书籍封面，设置为锁屏壁纸
 */
public class BookCoverService extends Service {

    private static final String TAG = "BookCoverService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "bookcover_service_channel";

    public static final String ACTION_STOP = "com.bookcover.wallpaper.STOP";
    public static final String ACTION_UPDATE = "com.bookcover.wallpaper.UPDATE";

    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable checkRunnable;
    private ForegroundAppDetector appDetector;
    private BookFileDetector fileDetector;
    private WallpaperHelper wallpaperHelper;
    private PrefsManager prefs;

    private String lastBookPath = null;
    private boolean isRunning = false;

    // 屏幕状态广播
    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                ensureCoverSet();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new PrefsManager(this);
        appDetector = new ForegroundAppDetector(this);
        fileDetector = new BookFileDetector(this);
        wallpaperHelper = new WallpaperHelper(this);

        // 创建通知渠道
        createNotificationChannel();

        // 启动前台通知（Android 8.0+ 必须在 5 秒内调用）
        startForeground(NOTIFICATION_ID, buildNotification("书籍封面壁纸服务运行中"));

        // 注册屏幕状态监听
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }

        Log.i(TAG, "服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isRunning) {
            isRunning = true;
            startChecking();
            Log.i(TAG, "开始监测阅读应用");
        }

        if (intent != null && ACTION_UPDATE.equals(intent.getAction())) {
            executor.execute(this::doCheck);
        }

        return START_STICKY;
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "书籍封面壁纸",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("监测阅读应用并更新锁屏封面");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 构建前台通知
     */
    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("书籍封面壁纸")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentIntent(pi)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_LOW);
        }

        return builder.build();
    }

    /**
     * 更新通知内容
     */
    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private void startChecking() {
        int interval = prefs.getCheckInterval() * 1000;
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && prefs.isEnabled()) {
                    executor.execute(BookCoverService.this::doCheck);
                }
                handler.postDelayed(this, interval);
            }
        };
        handler.post(checkRunnable);
    }

    /**
     * 核心检测逻辑
     */
    private void doCheck() {
        if (!prefs.isEnabled()) return;

        try {
            // 1. 检查前台是否为阅读应用
            String readerPkg = appDetector.getForegroundReadingApp();
            if (readerPkg == null) {
                return;
            }

            // 2. 检测当前书籍文件
            String bookPath = detectCurrentBook();
            if (bookPath == null) {
                return;
            }

            // 3. 书籍未变化则跳过
            if (bookPath.equals(lastBookPath)) {
                return;
            }

            Log.i(TAG, "检测到新书籍: " + bookPath);
            lastBookPath = bookPath;

            // 4. 提取封面
            int[] screenSize = wallpaperHelper.getScreenSize();
            Bitmap cover = CoverExtractor.extract(
                    bookPath,
                    screenSize[0],
                    screenSize[1],
                    prefs.isGrayscaleEnabled()
            );

            if (cover == null) {
                Log.w(TAG, "封面提取失败");
                return;
            }

            // 5. 可选：在封面上叠加书籍信息
            if (prefs.isShowBookInfoEnabled()) {
                String title = BookFileDetector.extractBookTitle(bookPath);
                cover = TextCoverGenerator.overlayInfo(cover, title, null);
            }

            // 6. 设置锁屏壁纸
            boolean success = wallpaperHelper.setLockScreenWallpaper(cover);
            if (success) {
                prefs.setLastBookPath(bookPath);
                prefs.setLastCoverPath(wallpaperHelper.getCoverCachePath());
                String bookTitle = BookFileDetector.extractBookTitle(bookPath);
                Log.i(TAG, "锁屏封面已更新: " + bookPath);

                // 更新通知
                updateNotification("当前书籍: " + bookTitle);

                // 发送广播通知 UI 更新
                sendUpdateBroadcast(bookPath, true);
            } else {
                sendUpdateBroadcast(bookPath, false);
            }

            if (!cover.isRecycled()) {
                cover.recycle();
            }

        } catch (Exception e) {
            Log.e(TAG, "检测过程出错", e);
        }
    }

    /**
     * 多策略检测当前书籍文件
     */
    private String detectCurrentBook() {
        // 策略1: 查询 MediaStore 中最近修改的电子书
        List<String> recentBooks = fileDetector.queryRecentEbooks(5, 60);
        if (!recentBooks.isEmpty()) {
            return recentBooks.get(0);
        }

        // 策略2: 扫描用户配置的目录
        List<String> scanDirs = prefs.getScanDirectories();
        for (String dir : scanDirs) {
            List<String> found = fileDetector.scanDirectoryForEbooks(dir, 3);
            if (!found.isEmpty()) {
                return found.get(0);
            }
        }

        return null;
    }

    /**
     * 确保封面已设置（息屏时调用）
     */
    private void ensureCoverSet() {
        String cachedCover = prefs.getLastCoverPath();
        if (cachedCover != null && new File(cachedCover).exists()) {
            wallpaperHelper.restoreFromCache();
        }
    }

    /**
     * 发送更新广播
     */
    private void sendUpdateBroadcast(String bookPath, boolean success) {
        Intent broadcast = new Intent("com.bookcover.wallpaper.COVER_UPDATED");
        broadcast.putExtra("book_path", bookPath);
        broadcast.putExtra("success", success);
        broadcast.setPackage(getPackageName());
        sendBroadcast(broadcast);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacks(checkRunnable);
        executor.shutdownNow();
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {}
        stopForeground(true);
        Log.i(TAG, "服务已销毁");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
