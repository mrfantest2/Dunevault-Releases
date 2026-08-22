package win.fantest.chatweblite;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class BrowserSessionActivity extends Activity {
    private static final int REQUEST_FILE_CHOOSER = 6401;
    private static final int REQUEST_STORAGE = 6402;
    private static final String PREF_LAST_HTTPS_URL = "last_https_url";
    private static final String STATE_TOUCH_LOCKED = "touch_locked";
    private static final long RECONNECT_POLL_MS = 3_000L;
    private static final String DISCONNECT_PROBE_JS =
            "(function(){try{" +
                    "var t=((document.body&&document.body.innerText)||'').toLowerCase();" +
                    "return t.indexOf('disconnected. attempting to reconnect')>=0" +
                    "||(t.indexOf('reload window')>=0&&t.indexOf('disconnected')>=0);" +
                    "}catch(e){return false;}})();";

    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());

    private LinearLayout rootLayout;
    private FrameLayout webContainer;
    private WebView webView;
    private ProgressBar pageProgress;
    private Button backButton;
    private Button forwardButton;
    private Button touchLockButton;
    private ValueCallback<Uri[]> fileChooserCallback;
    private PendingDownload pendingDownload;
    private boolean touchLocked;
    private boolean appForeground;
    private long disconnectedSinceMs = -1L;
    private long lastAutoRecoveryMs = -1L;

    private final Runnable reconnectProbe = new Runnable() {
        @Override
        public void run() {
            if (!appForeground || webView == null) return;

            final WebView observedWebView = webView;
            observedWebView.evaluateJavascript(DISCONNECT_PROBE_JS, result -> {
                if (!appForeground || observedWebView != webView) return;

                boolean disconnected = "true".equalsIgnoreCase(result);
                long now = SystemClock.elapsedRealtime();
                if (disconnected) {
                    if (disconnectedSinceMs < 0L) disconnectedSinceMs = now;
                    long disconnectedForMs = now - disconnectedSinceMs;
                    long sinceLastRecoveryMs = lastAutoRecoveryMs < 0L
                            ? Long.MAX_VALUE
                            : now - lastAutoRecoveryMs;

                    if (CodespaceReconnectPolicy.shouldRecover(
                            true,
                            true,
                            disconnectedForMs,
                            sinceLastRecoveryMs
                    )) {
                        lastAutoRecoveryMs = now;
                        disconnectedSinceMs = -1L;
                        observedWebView.reload();
                        Toast.makeText(
                                BrowserSessionActivity.this,
                                "Reconnecting Codespaces…",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                } else {
                    disconnectedSinceMs = -1L;
                }
            });

            reconnectHandler.postDelayed(this, RECONNECT_POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureSystemBars();
        buildInterface();
        installNewWebView();

        boolean restored = false;
        if (state != null) {
            touchLocked = state.getBoolean(STATE_TOUCH_LOCKED, false);
            restored = webView.restoreState(state) != null;
        }
        applyTouchLockState();

        if (!restored) {
            loadRecoveryUrl();
        } else {
            updateNavigationButtons();
        }
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(0xFF1F232B);
        getWindow().setNavigationBarColor(0xFF1F232B);
    }

    private void buildInterface() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(0xFF111318);
        applySystemBarInsets(rootLayout);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), dp(2), dp(4), dp(2));
        toolbar.setBackgroundColor(0xFF1F232B);

        backButton = toolbarButton("‹");
        forwardButton = toolbarButton("›");
        Button homeButton = toolbarButton("⌂");
        Button reloadButton = toolbarButton("↻");
        touchLockButton = toolbarButton("🔓");
        Button externalButton = toolbarButton("↗");

        touchLockButton.setTextSize(16f);
        touchLockButton.setContentDescription("Lock web touch input");

        toolbar.addView(backButton, toolbarParams());
        toolbar.addView(forwardButton, toolbarParams());
        toolbar.addView(homeButton, toolbarParams());
        toolbar.addView(reloadButton, toolbarParams());
        toolbar.addView(touchLockButton, toolbarParams());
        toolbar.addView(externalButton, toolbarParams());
        rootLayout.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        ));

        pageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);
        pageProgress.setProgress(0);
        pageProgress.setVisibility(View.GONE);
        rootLayout.addView(pageProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(2)
        ));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(0xFF111318);
        rootLayout.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(rootLayout);
        rootLayout.post(rootLayout::requestApplyInsets);

        backButton.setOnClickListener(v -> {
            if (webView != null && webView.canGoBack()) webView.goBack();
        });
        forwardButton.setOnClickListener(v -> {
            if (webView != null && webView.canGoForward()) webView.goForward();
        });
        homeButton.setOnClickListener(v -> {
            if (webView != null) webView.loadUrl(CodespaceUrlPolicy.HOME_URL);
        });
        reloadButton.setOnClickListener(v -> {
            if (webView != null) webView.reload();
        });
        touchLockButton.setOnClickListener(v -> setTouchLocked(!touchLocked));
        externalButton.setOnClickListener(v -> {
            if (webView == null) return;
            String target = CodespaceUrlPolicy.recoveryUrlOrHome(webView.getUrl());
            openExternal(Uri.parse(target));
        });
    }

    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left;
            int top;
            int right;
            int bottom;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                left = insets.left;
                top = insets.top;
                right = insets.right;
                bottom = insets.bottom;
            } else {
                left = windowInsets.getSystemWindowInsetLeft();
                top = windowInsets.getSystemWindowInsetTop();
                right = windowInsets.getSystemWindowInsetRight();
                bottom = windowInsets.getSystemWindowInsetBottom();
            }

            view.setPadding(left, top, right, bottom);
            return windowInsets;
        });
    }

    private Button toolbarButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(20f);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(1), 0, dp(1), 0);
        return button;
    }

    private LinearLayout.LayoutParams toolbarParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(1), 0, dp(1), 0);
        return params;
    }

    private void installNewWebView() {
        webView = createConfiguredWebView();
        webContainer.removeAllViews();
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        applyTouchLockState();
        updateNavigationButtons();
    }

    private WebView createConfiguredWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(0xFF111318);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setOnTouchListener((ignored, event) -> touchLocked);

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        String defaultUserAgent = settings.getUserAgentString();
        if (defaultUserAgent != null && !defaultUserAgent.contains("FantestCodespace/")) {
            settings.setUserAgentString(defaultUserAgent + " FantestCodespace/1.0.1");
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView source, WebResourceRequest request) {
                Uri uri = request == null ? null : request.getUrl();
                return handleNavigation(uri);
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView source, String url) {
                return handleNavigation(url == null ? null : Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView source, String url) {
                super.onPageFinished(source, url);
                if (CodespaceUrlPolicy.isInternalWebUrl(url)) {
                    getPreferences(MODE_PRIVATE)
                            .edit()
                            .putString(PREF_LAST_HTTPS_URL, url)
                            .apply();
                    CookieManager.getInstance().flush();
                }
                disconnectedSinceMs = -1L;
                updateNavigationButtons();
            }

            @Override
            public boolean onRenderProcessGone(WebView source, RenderProcessGoneDetail detail) {
                if (source != webView) return false;

                String recoveryUrl = getStoredRecoveryUrl();
                webContainer.removeView(source);
                source.destroy();
                installNewWebView();
                webView.loadUrl(recoveryUrl);
                Toast.makeText(
                        BrowserSessionActivity.this,
                        detail != null && detail.didCrash()
                                ? "WebView recovered after a renderer crash"
                                : "WebView restored after Android reclaimed the renderer",
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView source, int newProgress) {
                super.onProgressChanged(source, newProgress);
                if (pageProgress == null) return;
                pageProgress.setProgress(newProgress);
                pageProgress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView source,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = filePathCallback;

                Intent intent;
                try {
                    intent = fileChooserParams == null
                            ? new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            : fileChooserParams.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    if (fileChooserParams != null
                            && fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    startActivityForResult(intent, REQUEST_FILE_CHOOSER);
                    return true;
                } catch (Throwable error) {
                    if (fileChooserCallback != null) {
                        fileChooserCallback.onReceiveValue(null);
                        fileChooserCallback = null;
                    }
                    Toast.makeText(
                            BrowserSessionActivity.this,
                            "No document picker is available",
                            Toast.LENGTH_LONG
                    ).show();
                    return false;
                }
            }
        });

        view.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (!CodespaceUrlPolicy.isInternalWebUrl(url)) {
                Toast.makeText(this, "Blocked non-HTTPS download", Toast.LENGTH_LONG).show();
                return;
            }

            PendingDownload download = new PendingDownload(
                    url,
                    userAgent,
                    contentDisposition,
                    mimeType
            );

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = download;
                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_STORAGE
                );
                return;
            }

            enqueueDownload(download);
        });

        return view;
    }

    private void setTouchLocked(boolean locked) {
        touchLocked = locked;
        applyTouchLockState();
        Toast.makeText(
                this,
                locked ? "Touch input locked" : "Touch input unlocked",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void applyTouchLockState() {
        if (touchLockButton != null) {
            touchLockButton.setText(touchLocked ? "🔒" : "🔓");
            touchLockButton.setContentDescription(
                    touchLocked ? "Unlock web touch input" : "Lock web touch input"
            );
        }
        if (webView != null) {
            webView.setFocusable(!touchLocked);
            webView.setFocusableInTouchMode(!touchLocked);
            if (touchLocked) {
                webView.clearFocus();
                hideKeyboard();
            }
        }
    }

    private void hideKeyboard() {
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard == null) return;
        View tokenView = webView != null ? webView : rootLayout;
        if (tokenView != null) {
            keyboard.hideSoftInputFromWindow(tokenView.getWindowToken(), 0);
        }
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return true;
        String url = uri.toString();
        if (CodespaceUrlPolicy.isInternalWebUrl(url)) {
            return false;
        }
        openExternal(uri);
        return true;
    }

    private boolean openExternal(Uri uri) {
        if (uri == null) return false;
        try {
            Intent intent;
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getPackageManager()) == null) return false;
            startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void loadRecoveryUrl() {
        if (webView != null) webView.loadUrl(getStoredRecoveryUrl());
    }

    private String getStoredRecoveryUrl() {
        String lastUrl = getPreferences(MODE_PRIVATE)
                .getString(PREF_LAST_HTTPS_URL, null);
        return CodespaceUrlPolicy.recoveryUrlOrHome(lastUrl);
    }

    private void updateNavigationButtons() {
        if (backButton != null) {
            backButton.setEnabled(webView != null && webView.canGoBack());
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(webView != null && webView.canGoForward());
        }
    }

    private void enqueueDownload(PendingDownload download) {
        if (download == null) return;
        try {
            Uri uri = Uri.parse(download.url);
            String fileName = URLUtil.guessFileName(
                    download.url,
                    download.contentDisposition,
                    download.mimeType
            );

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(fileName);
            request.setDescription("Downloaded from Fantest Codespace");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            if (download.mimeType != null && !download.mimeType.trim().isEmpty()) {
                request.setMimeType(download.mimeType);
            }
            if (download.userAgent != null && !download.userAgent.trim().isEmpty()) {
                request.addRequestHeader("User-Agent", download.userAgent);
            }

            String cookie = CookieManager.getInstance().getCookie(download.url);
            if (cookie != null && !cookie.trim().isEmpty()) {
                request.addRequestHeader("Cookie", cookie);
            }

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("DownloadManager unavailable");
            manager.enqueue(request);
            Toast.makeText(this, "Download started: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Toast.makeText(this, "Download could not be started", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILE_CHOOSER) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    List<Uri> uris = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        if (uri != null) uris.add(uri);
                    }
                    results = uris.toArray(new Uri[0]);
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else {
                    results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                }
            }

            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(results);
                fileChooserCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE) return;

        PendingDownload download = pendingDownload;
        pendingDownload = null;
        if (download == null) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enqueueDownload(download);
        } else {
            Toast.makeText(this, "Storage permission is required for downloads", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        appForeground = true;
        disconnectedSinceMs = -1L;
        reconnectHandler.removeCallbacks(reconnectProbe);
        reconnectHandler.postDelayed(reconnectProbe, RECONNECT_POLL_MS);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_TOUCH_LOCKED, touchLocked);
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        appForeground = false;
        disconnectedSinceMs = -1L;
        reconnectHandler.removeCallbacks(reconnectProbe);
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        appForeground = false;
        reconnectHandler.removeCallbacksAndMessages(null);
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        if (webView != null) {
            webContainer.removeView(webView);
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class PendingDownload {
        final String url;
        final String userAgent;
        final String contentDisposition;
        final String mimeType;

        PendingDownload(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType
        ) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
        }
    }
}
