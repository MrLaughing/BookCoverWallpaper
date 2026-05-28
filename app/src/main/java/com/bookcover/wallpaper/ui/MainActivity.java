package com.bookcover.wallpaper.ui;

import android.Manifest;
import android.app.Activity;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bookcover.wallpaper.R;
import com.bookcover.wallpaper.detector.BookFileDetector;
import com.bookcover.wallpaper.detector.ForegroundAppDetector;
import com.bookcover.wallpaper.extractor.CoverExtractor;
import com.bookcover.wallpaper.extractor.WallpaperHelper;
import com.bookcover.wallpaper.service.BookCoverService;
import com.bookcover.wallpaper.util.PrefsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 主界面 - 设置和管理
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_USAGE_STATS = 1001;
    private static final int REQUEST_READ_STORAGE = 1002;
    private static final int REQUEST_PICK_FILE = 1003;

    private PrefsManager prefs;
    private ForegroundAppDetector appDetector;
    private WallpaperHelper wallpaperHelper;

    private Switch switchEnabled;
    private Switch switchGrayscale;
    private Switch switchBookInfo;
    private TextView tvStatus;
    private TextView tvCurrentBook;
    private ImageView ivCoverPreview;
    private TextView tvCheckInterval;
    private LinearLayout layoutScanDirs;
    private LinearLayout layoutCustomReaders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);
        appDetector = new ForegroundAppDetector(this);
        wallpaperHelper = new WallpaperHelper(this);

        initViews();
        loadSettings();
        checkPermissions();
        updateStatus();
    }

    private void initViews() {
        switchEnabled = findViewById(R.id.switch_enabled);
        switchGrayscale = findViewById(R.id.switch_grayscale);
        switchBookInfo = findViewById(R.id.switch_book_info);
        tvStatus = findViewById(R.id.tv_status);
        tvCurrentBook = findViewById(R.id.tv_current_book);
        ivCoverPreview = findViewById(R.id.iv_cover_preview);
        tvCheckInterval = findViewById(R.id.tv_check_interval);
        layoutScanDirs = findViewById(R.id.layout_scan_dirs);
        layoutCustomReaders = findViewById(R.id.layout_custom_readers);

        // 服务开关
        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setEnabled(isChecked);
            if (isChecked) {
                startCoverService();
            } else {
                stopCoverService();
            }
            updateStatus();
        });

        // 灰度模式
        switchGrayscale.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setGrayscaleEnabled(isChecked);
        });

        // 书籍信息叠加
        switchBookInfo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setShowBookInfoEnabled(isChecked);
        });

        // 检查间隔
        findViewById(R.id.btn_interval_minus).setOnClickListener(v -> {
            int interval = prefs.getCheckInterval();
            if (interval > 1) {
                prefs.setCheckInterval(interval - 1);
                tvCheckInterval.setText(interval - 1 + " 秒");
            }
        });

        findViewById(R.id.btn_interval_plus).setOnClickListener(v -> {
            int interval = prefs.getCheckInterval();
            if (interval < 60) {
                prefs.setCheckInterval(interval + 1);
                tvCheckInterval.setText(interval + 1 + " 秒");
            }
        });

        // 手动选择封面
        findViewById(R.id.btn_pick_cover).setOnClickListener(v -> {
            openFilePicker();
        });

        // 添加扫描目录
        findViewById(R.id.btn_add_dir).setOnClickListener(v -> {
            showAddDirectoryDialog();
        });

        // 添加自定义阅读器
        findViewById(R.id.btn_add_reader).setOnClickListener(v -> {
            showAddReaderDialog();
        });

        // 立即检测
        findViewById(R.id.btn_check_now).setOnClickListener(v -> {
            triggerImmediateCheck();
        });

        // 权限设置按钮
        findViewById(R.id.btn_permission_usage).setOnClickListener(v -> {
            requestUsageStatsPermission();
        });

        findViewById(R.id.btn_permission_storage).setOnClickListener(v -> {
            requestStoragePermission();
        });

        // 恢复默认封面
        findViewById(R.id.btn_restore).setOnClickListener(v -> {
            wallpaperHelper.restoreFromCache();
            loadCoverPreview();
            Toast.makeText(this, "已恢复上次封面", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadSettings() {
        switchEnabled.setChecked(prefs.isEnabled());
        switchGrayscale.setChecked(prefs.isGrayscaleEnabled());
        switchBookInfo.setChecked(prefs.isShowBookInfoEnabled());
        tvCheckInterval.setText(prefs.getCheckInterval() + " 秒");

        // 加载当前书籍信息
        String lastBook = prefs.getLastBookPath();
        if (lastBook != null) {
            tvCurrentBook.setText(BookFileDetector.extractBookTitle(lastBook));
        }

        // 加载扫描目录列表
        refreshScanDirsList();

        // 加载自定义阅读器列表
        refreshCustomReadersList();

        // 加载封面预览
        loadCoverPreview();
    }

    private void loadCoverPreview() {
        String coverPath = wallpaperHelper.getCoverCachePath();
        File coverFile = new File(coverPath);
        if (coverFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(coverPath);
            if (bitmap != null) {
                ivCoverPreview.setImageBitmap(bitmap);
                ivCoverPreview.setVisibility(View.VISIBLE);
                return;
            }
        }
        ivCoverPreview.setImageResource(R.drawable.ic_book_placeholder);
    }

    private void checkPermissions() {
        boolean hasUsage = ForegroundAppDetector.hasUsageStatsPermission(this);
        boolean hasStorage = checkStoragePermission();

        findViewById(R.id.layout_permission_usage).setVisibility(
                hasUsage ? View.GONE : View.VISIBLE);
        findViewById(R.id.layout_permission_storage).setVisibility(
                hasStorage ? View.GONE : View.VISIBLE);
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return true; // Android 11+ 通过 MediaStore 访问
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        try {
            startActivityForResult(intent, REQUEST_USAGE_STATS);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开使用情况设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_STORAGE);
        }
    }

    private void startCoverService() {
        Intent intent = new Intent(this, BookCoverService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopCoverService() {
        Intent intent = new Intent(this, BookCoverService.class);
        intent.setAction(BookCoverService.ACTION_STOP);
        startService(intent);
    }

    private void triggerImmediateCheck() {
        Intent intent = new Intent(this, BookCoverService.class);
        intent.setAction(BookCoverService.ACTION_UPDATE);
        startService(intent);
        Toast.makeText(this, "正在检测...", Toast.LENGTH_SHORT).show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/epub+zip", "application/pdf",
                "image/png", "image/jpeg", "image/jpg"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择封面图片或电子书"), REQUEST_PICK_FILE);
    }

    private void showAddDirectoryDialog() {
        EditText input = new EditText(this);
        input.setHint("/sdcard/Books");
        new AlertDialog.Builder(this)
                .setTitle("添加扫描目录")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> {
                    String dir = input.getText().toString().trim();
                    if (!dir.isEmpty()) {
                        prefs.addScanDirectory(dir);
                        refreshScanDirsList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddReaderDialog() {
        EditText input = new EditText(this);
        input.setHint("com.example.reader");
        new AlertDialog.Builder(this)
                .setTitle("添加阅读器包名")
                .setMessage("输入阅读应用的包名")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> {
                    String pkg = input.getText().toString().trim();
                    if (!pkg.isEmpty()) {
                        prefs.addCustomReaderPackage(pkg);
                        appDetector.addCustomReaderPackage(pkg);
                        refreshCustomReadersList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshScanDirsList() {
        List<String> dirs = prefs.getScanDirectories();
        layoutScanDirs.removeAllViews();
        for (String dir : dirs) {
            View item = createDirItem(dir);
            layoutScanDirs.addView(item);
        }
    }

    private View createDirItem(String dir) {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.item_list_entry, layoutScanDirs, false);
        TextView tvName = view.findViewById(R.id.tv_name);
        Button btnRemove = view.findViewById(R.id.btn_remove);

        tvName.setText(dir);
        btnRemove.setOnClickListener(v -> {
            prefs.removeScanDirectory(dir);
            refreshScanDirsList();
        });
        return view;
    }

    private void refreshCustomReadersList() {
        List<String> readers = prefs.getCustomReaderPackages();
        layoutCustomReaders.removeAllViews();
        for (String reader : readers) {
            View item = createReaderItem(reader);
            layoutCustomReaders.addView(item);
        }
    }

    private View createReaderItem(String pkg) {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.item_list_entry, layoutCustomReaders, false);
        TextView tvName = view.findViewById(R.id.tv_name);
        Button btnRemove = view.findViewById(R.id.btn_remove);

        tvName.setText(pkg);
        tvName.setTextSize(12);
        btnRemove.setOnClickListener(v -> {
            prefs.removeCustomReaderPackage(pkg);
            appDetector.removeCustomReaderPackage(pkg);
            refreshCustomReadersList();
        });
        return view;
    }

    private void updateStatus() {
        boolean hasUsage = ForegroundAppDetector.hasUsageStatsPermission(this);
        boolean enabled = prefs.isEnabled();

        if (!hasUsage) {
            tvStatus.setText("⚠ 需要授予「使用情况访问」权限");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        } else if (enabled) {
            String reader = appDetector.getForegroundReadingApp();
            if (reader != null) {
                tvStatus.setText("✓ 正在监测阅读应用");
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                tvStatus.setText("✓ 服务运行中，等待阅读应用");
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            }
        } else {
            tvStatus.setText("✗ 服务未启用");
            tvStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_USAGE_STATS) {
            checkPermissions();
            updateStatus();
        } else if (requestCode == REQUEST_PICK_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                handlePickedFile(data.getData());
            }
        }
    }

    private void handlePickedFile(Uri uri) {
        try {
            String path = uri.getPath();
            if (path == null) return;

            // 如果是图片，直接设为壁纸
            if (path.toLowerCase().endsWith(".png")
                    || path.toLowerCase().endsWith(".jpg")
                    || path.toLowerCase().endsWith(".jpeg")) {
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    wallpaperHelper.setLockScreenWallpaper(bitmap);
                    loadCoverPreview();
                    Toast.makeText(this, "封面已设置", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 如果是电子书，提取封面
                int[] screenSize = wallpaperHelper.getScreenSize();
                Bitmap cover = CoverExtractor.extract(
                        path, screenSize[0], screenSize[1], prefs.isGrayscaleEnabled());
                if (cover != null) {
                    wallpaperHelper.setLockScreenWallpaper(cover);
                    loadCoverPreview();
                    Toast.makeText(this, "封面已提取并设置", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "无法提取封面", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理文件失败", e);
            Toast.makeText(this, "处理失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_STORAGE) {
            checkPermissions();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
        updateStatus();
        loadCoverPreview();
    }
}
