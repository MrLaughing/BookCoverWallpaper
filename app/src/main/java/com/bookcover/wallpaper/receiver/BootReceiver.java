package com.bookcover.wallpaper.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bookcover.wallpaper.service.BookCoverService;
import com.bookcover.wallpaper.util.PrefsManager;

/**
 * 开机自启接收器
 * 确保设备重启后服务自动启动（仅在用户已启用服务时）
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // 先检查用户是否启用了服务
            PrefsManager prefs = new PrefsManager(context);
            if (!prefs.isEnabled()) {
                Log.i(TAG, "服务未启用，跳过自动启动");
                return;
            }

            Log.i(TAG, "设备启动完成，启动 BookCoverService");
            Intent serviceIntent = new Intent(context, BookCoverService.class);
            try {
                context.startForegroundService(serviceIntent);
            } catch (Exception e) {
                Log.e(TAG, "启动服务失败", e);
            }
        }
    }
}
