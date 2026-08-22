package win.fantest.chatweblite;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

final class BackgroundReadiness {
    enum Status {
        READY,
        NEEDS_ACTION,
        REVIEW
    }

    private BackgroundReadiness() {
    }

    static Status notificationStatus(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return Status.READY;
        }
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
                ? Status.READY
                : Status.NEEDS_ACTION;
    }

    static Status batteryStatus(Context context) {
        PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(context.getPackageName())
                ? Status.READY
                : Status.NEEDS_ACTION;
    }

    static Status unusedAppStatus(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return context.getPackageManager().isAutoRevokeWhitelisted()
                    ? Status.READY
                    : Status.NEEDS_ACTION;
        }
        return Status.REVIEW;
    }

    static Intent batteryOptimizationIntent(Context context) {
        return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + context.getPackageName()));
    }

    static Intent genericBatteryOptimizationIntent() {
        return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    }

    static Intent appDetailsIntent(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + context.getPackageName()));
    }

    static boolean allReady(Context context) {
        return notificationStatus(context) == Status.READY
                && batteryStatus(context) == Status.READY
                && unusedAppStatus(context) == Status.READY;
    }
}
