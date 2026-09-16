package win.fantest.chatweblite;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.PixelCopy;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONTokener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.TimeZone;

final class BrowserUtilityActions {
    private static final int SEND_RETRY_LIMIT = 20;
    private static final long SEND_RETRY_DELAY_MS = 250L;

    private BrowserUtilityActions() {
    }

    static void saveVisibleWebViewScreenshot(Activity activity, WebView target) {
        if (!isUsable(activity, target)) return;
        int width = target.getWidth();
        int height = target.getHeight();
        if (width <= 0 || height <= 0) {
            Toast.makeText(activity, "WebView is not ready for a screenshot", Toast.LENGTH_LONG).show();
            return;
        }

        int[] location = new int[2];
        target.getLocationInWindow(location);
        Rect source = new Rect(
                location[0],
                location[1],
                location[0] + width,
                location[1] + height
        );
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (Throwable error) {
            Toast.makeText(activity, "Could not allocate screenshot", Toast.LENGTH_LONG).show();
            return;
        }

        PixelCopy.request(
                activity.getWindow(),
                source,
                bitmap,
                result -> {
                    if (result != PixelCopy.SUCCESS) {
                        bitmap.recycle();
                        if (isUsable(activity, target)) {
                            Toast.makeText(activity, "Screenshot capture failed", Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    persistScreenshotAsync(activity, bitmap);
                },
                new Handler(Looper.getMainLooper())
        );
    }

    static void copyLastChatOutput(Activity activity, WebView target) {
        if (!isUsable(activity, target)) return;
        target.evaluateJavascript(ChatDomBridge.copyLastOutputScript(), result -> {
            if (!isUsable(activity, target)) return;
            String text = decodeJavaScriptString(result);
            if (text.trim().isEmpty()) {
                Toast.makeText(activity, "No chat output found", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                Toast.makeText(activity, "Clipboard is unavailable", Toast.LENGTH_LONG).show();
                return;
            }
            clipboard.setPrimaryClip(
                    ClipData.newPlainText("Fantest Codespace chat output", text)
            );
            Toast.makeText(activity, "Last chat output copied", Toast.LENGTH_SHORT).show();
        });
    }

    static void pasteClipboardPromptAndSend(
            Activity activity,
            WebView target,
            Handler handler,
            Runnable onFinished
    ) {
        if (!isUsable(activity, target)) {
            finish(onFinished);
            return;
        }

        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()
                || clipboard.getPrimaryClip() == null
                || clipboard.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(activity, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            finish(onFinished);
            return;
        }

        CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity);
        String text = value == null ? "" : value.toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(activity, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            finish(onFinished);
            return;
        }

        target.evaluateJavascript(ChatDomBridge.pastePromptScript(text), result -> {
            if (!isUsable(activity, target)) {
                finish(onFinished);
                return;
            }
            String code = decodeJavaScriptString(result);
            if (!"inserted".equals(code)) {
                Toast.makeText(
                        activity,
                        "input-missing".equals(code)
                                ? "Visible chat input not found"
                                : "Prompt could not be pasted",
                        Toast.LENGTH_LONG
                ).show();
                finish(onFinished);
                return;
            }
            handler.postDelayed(
                    () -> attemptSend(activity, target, handler, 0, onFinished),
                    SEND_RETRY_DELAY_MS
            );
        });
    }

    private static void attemptSend(
            Activity activity,
            WebView target,
            Handler handler,
            int attempt,
            Runnable onFinished
    ) {
        if (!isUsable(activity, target)) {
            finish(onFinished);
            return;
        }
        target.evaluateJavascript(ChatDomBridge.sendPromptScript(), result -> {
            if (!isUsable(activity, target)) {
                finish(onFinished);
                return;
            }
            String code = decodeJavaScriptString(result);
            if ("sent".equals(code)) {
                Toast.makeText(activity, "Prompt sent", Toast.LENGTH_SHORT).show();
                finish(onFinished);
                return;
            }
            if ("input-missing".equals(code)) {
                Toast.makeText(activity, "Visible chat input not found", Toast.LENGTH_LONG).show();
                finish(onFinished);
                return;
            }
            int next = attempt + 1;
            if (next >= SEND_RETRY_LIMIT) {
                Toast.makeText(activity, "Send button is not ready", Toast.LENGTH_LONG).show();
                finish(onFinished);
                return;
            }
            handler.postDelayed(
                    () -> attemptSend(activity, target, handler, next, onFinished),
                    SEND_RETRY_DELAY_MS
            );
        });
    }

    private static void persistScreenshotAsync(Activity activity, Bitmap bitmap) {
        final String fileName = ScreenshotPolicy.fileName(
                System.currentTimeMillis(),
                TimeZone.getDefault()
        );
        new Thread(() -> {
            boolean success = false;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveScopedScreenshot(activity, bitmap, fileName);
                } else {
                    saveLegacyScreenshot(bitmap, fileName);
                }
                success = true;
            } catch (Throwable ignored) {
            } finally {
                bitmap.recycle();
            }

            boolean saved = success;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                Toast.makeText(
                        activity,
                        saved ? "Saved screenshot: " + fileName : "Screenshot could not be saved",
                        saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
                ).show();
            });
        }, "FantestCodespace-Screenshot").start();
    }

    private static void saveScopedScreenshot(
            Activity activity,
            Bitmap bitmap,
            String fileName
    ) throws Exception {
        ContentResolver resolver = activity.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, ScreenshotPolicy.RELATIVE_DIRECTORY);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("MediaStore insert failed");
        boolean committed = false;
        try {
            try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IllegalStateException("PNG write failed");
                }
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, ready, null, null);
            committed = true;
        } finally {
            if (!committed) {
                try {
                    resolver.delete(uri, null, null);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void saveLegacyScreenshot(Bitmap bitmap, String fileName) throws Exception {
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File directory = new File(pictures, "FantestCodespace");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Screenshot directory unavailable");
        }
        File destination = new File(directory, fileName);
        try (OutputStream output = new FileOutputStream(destination)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IllegalStateException("PNG write failed");
            }
        }
    }

    private static String decodeJavaScriptString(String result) {
        if (result == null || result.isEmpty() || "null".equals(result)) return "";
        try {
            Object value = new JSONTokener(result).nextValue();
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isUsable(Activity activity, WebView target) {
        return activity != null
                && target != null
                && !activity.isFinishing()
                && !activity.isDestroyed();
    }

    private static void finish(Runnable onFinished) {
        if (onFinished != null) onFinished.run();
    }
}
