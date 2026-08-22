package win.fantest.chatweblite;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

    private FrameLayout webContainer;
    private WebView webView;
    private ProgressBar pageProgress;
    private Button backButton;
    private Button forwardButton;
    private ValueCallback<Uri[]> fileChooserCallback;
    private PendingDownload pendingDownload;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildInterface();
        installNewWebView();

        boolean restored = false;
        if (state != null) {
            restored = webView.restoreState(state) != null;
        }
        if (!restored) {
            loadRecoveryUrl();
        } else {
            updateNavigationButtons();
        }
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF111318);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), dp(2), dp(4), dp(2));
        toolbar.setBackgroundColor(0xFF1F232B);

        backButton = toolbarButton("‹");
        forwardButton = toolbarButton("›");
        Button homeButton = toolbarButton("⌂");
        Button reloadButton = toolbarButton("↻");
        Button externalButton = toolbarButton("↗");

        toolbar.addView(backButton, toolbarParams());
        toolbar.addView(forwardButton, toolbarParams());
        toolbar.addView(homeButton, toolbarParams());
        toolbar.addView(reloadButton, toolbarParams());
        toolbar.addView(externalButton, toolbarParams());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        ));

        pageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);
        pageProgress.setProgress(0);
        pageProgress.setVisibility(View.GONE);
        root.addView(pageProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(2)
        ));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(0xFF111318);
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);

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
        externalButton.setOnClickListener(v -> {
            if (webView == null) return;
            String target = CodespaceUrlPolicy.recoveryUrlOrHome(webView.getUrl());
            openExternal(Uri.parse(target));
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
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
    }

    private LinearLayout.LayoutParams toolbarParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
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
        updateNavigationButtons();
    }

    private WebView createConfiguredWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(0xFF111318);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);

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
            settings.setUserAgentString(defaultUserAgent + " FantestCodespace/1.0");
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
                updateNavigationButtons();
            }

            @Override
            public boolean onRenderProcessGone(WebView source, RenderProcessGoneDetail detail) {
                if (source != webView) return false;
                String recoveryUrl = CodespaceUrlPolicy.recoveryUrlOrHome(source.getUrl());
                webContainer.removeView(source);
                source.destroy();
                installNewWebView();
                if (CodespaceUrlPolicy.HOME_URL.equals(recoveryUrl)) {
                    recoveryUrl = getStoredRecoveryUrl();
                }
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
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
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
