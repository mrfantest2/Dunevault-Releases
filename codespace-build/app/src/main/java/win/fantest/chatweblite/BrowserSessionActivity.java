package win.fantest.chatweblite;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class BrowserSessionActivity extends Activity {
    private static final int REQUEST_FILE_CHOOSER = 6401;
    private static final int REQUEST_STORAGE = 6402;
    private static final int REQUEST_NOTIFICATIONS = 6403;

    private static final String PREF_LAST_HTTPS_URL = "last_https_url";
    private static final String PREF_SETUP_PROMPTED_V102 = "background_setup_prompted_v102";
    private static final String STATE_TOUCH_LOCKED = "touch_locked";

    private static final long CONNECTION_PROBE_INTERVAL_MS = 3_000L;

    private static final String CONNECTION_PROBE_JS =
            "(function(){try{" +
                    "var t=((document.body&&document.body.innerText)||'').toLowerCase();" +
                    "if(t.indexOf('oh no, it looks like you are offline')>=0||" +
                    "t.indexOf('failed to fetch')>=0||" +
                    "t.indexOf('retry connecting to the codespace')>=0)return 'offline';" +
                    "if(t.indexOf('disconnected. attempting to reconnect')>=0||" +
                    "(t.indexOf('reload window')>=0&&t.indexOf('disconnected')>=0))return 'disconnected';" +
                    "return 'healthy';}catch(e){return 'healthy';}})();";

    private static final int MENU_KEEP_ALIVE = 1;
    private static final int MENU_BACKGROUND_SETUP = 2;
    private static final int MENU_RECONNECT = 3;
    private static final int MENU_EXTERNAL = 4;

    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final CodespaceReconnectPolicy reconnectPolicy = new CodespaceReconnectPolicy();

    private FrameLayout webContainer;
    private WebView webView;
    private ProgressBar pageProgress;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private ImageButton touchLockButton;
    private ValueCallback<Uri[]> fileChooserCallback;
    private PendingDownload pendingDownload;
    private AlertDialog setupDialog;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkCallbackRegistered;
    private volatile boolean networkValidated;
    private boolean appForeground;
    private boolean touchLocked;

    private final Runnable reconnectProbe = new Runnable() {
        @Override
        public void run() {
            if (!appForeground || webView == null) return;

            reconnectHandler.postDelayed(this, CONNECTION_PROBE_INTERVAL_MS);
            final WebView observedWebView = webView;
            observedWebView.evaluateJavascript(CONNECTION_PROBE_JS, result -> {
                if (!appForeground || observedWebView != webView) return;

                CodespaceReconnectPolicy.Signal signal = parseConnectionSignal(result);
                CodespaceReconnectPolicy.Decision decision = reconnectPolicy.onProbe(
                        appForeground,
                        networkValidated,
                        signal,
                        SystemClock.elapsedRealtime()
                );
                if (decision == CodespaceReconnectPolicy.Decision.RELOAD) {
                    observedWebView.reload();
                    Toast.makeText(
                            BrowserSessionActivity.this,
                            "Reconnecting Codespaces…",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        CodespaceKeepAliveService.start(this);

        touchLocked = state != null && state.getBoolean(STATE_TOUCH_LOCKED, false);
        configureSystemBars();
        buildInterface();
        installNewWebView();
        startNetworkObservation();

        boolean restored = false;
        if (state != null) {
            restored = webView.restoreState(state) != null;
        }
        if (!restored) {
            loadRecoveryUrl();
        } else {
            updateNavigationButtons();
            applyTouchLock();
        }

        maybeShowBackgroundSetup();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(0xFF1F232B);
        getWindow().setNavigationBarColor(0xFF1F232B);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF111318);
        applySafeInsets(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), dp(2), dp(4), dp(2));
        toolbar.setBackgroundColor(0xFF1F232B);

        backButton = toolbarButton(R.drawable.ic_toolbar_back, "Back");
        forwardButton = toolbarButton(R.drawable.ic_toolbar_forward, "Forward");
        ImageButton homeButton = toolbarButton(R.drawable.ic_toolbar_home, "Codespace home");
        ImageButton reloadButton = toolbarButton(R.drawable.ic_toolbar_refresh, "Refresh");
        touchLockButton = toolbarButton(R.drawable.ic_toolbar_lock_open, "Lock touch input");
        ImageButton moreButton = toolbarButton(R.drawable.ic_toolbar_more, "More options");

        toolbar.addView(backButton, toolbarParams());
        toolbar.addView(forwardButton, toolbarParams());
        toolbar.addView(homeButton, toolbarParams());
        toolbar.addView(reloadButton, toolbarParams());
        toolbar.addView(touchLockButton, toolbarParams());
        toolbar.addView(moreButton, toolbarParams());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
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
        touchLockButton.setOnClickListener(v -> {
            touchLocked = !touchLocked;
            applyTouchLock();
            Toast.makeText(
                    this,
                    touchLocked ? "Touch Lock enabled" : "Touch Lock disabled",
                    Toast.LENGTH_SHORT
            ).show();
        });
        moreButton.setOnClickListener(this::showMoreMenu);
        updateTouchLockButton();
    }

    private void applySafeInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            } else {
                view.setPadding(
                        windowInsets.getSystemWindowInsetLeft(),
                        windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(),
                        windowInsets.getSystemWindowInsetBottom()
                );
            }
            return windowInsets;
        });
        root.requestApplyInsets();
    }

    private ImageButton toolbarButton(int iconRes, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setMinimumWidth(dp(48));
        button.setMinimumHeight(dp(48));
        button.setBackground(toolbarButtonBackground(false));
        button.setColorFilter(Color.WHITE);
        return button;
    }

    private LinearLayout.LayoutParams toolbarParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private StateListDrawable toolbarButtonBackground(boolean active) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(
                new int[]{android.R.attr.state_pressed},
                roundedBackground(0xFF343A46)
        );
        drawable.addState(
                new int[]{},
                roundedBackground(active ? 0xFF264F78 : 0x00000000)
        );
        return drawable;
    }

    private GradientDrawable roundedBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        MenuItem keepAlive = popup.getMenu().add(0, MENU_KEEP_ALIVE, 0, "Keep Alive: Active");
        keepAlive.setEnabled(false);
        popup.getMenu().add(0, MENU_BACKGROUND_SETUP, 1, "Background setup");
        popup.getMenu().add(0, MENU_RECONNECT, 2, "Reconnect now");
        popup.getMenu().add(0, MENU_EXTERNAL, 3, "Open in browser");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_BACKGROUND_SETUP:
                    showBackgroundSetup();
                    return true;
                case MENU_RECONNECT:
                    if (webView != null) {
                        webView.reload();
                        Toast.makeText(this, "Reconnect requested", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case MENU_EXTERNAL:
                    if (webView != null) {
                        String target = CodespaceUrlPolicy.recoveryUrlOrHome(webView.getUrl());
                        openExternal(Uri.parse(target));
                    }
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void installNewWebView() {
        webView = createConfiguredWebView();
        webContainer.removeAllViews();
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        applyTouchLock();
        updateNavigationButtons();
    }

    private WebView createConfiguredWebView() {
        WebView view = new WebView(this);
        view.setBackgroundColor(0xFF111318);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
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
            settings.setUserAgentString(defaultUserAgent + " FantestCodespace/1.0.2");
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

    private void applyTouchLock() {
        if (webView != null) {
            if (touchLocked) {
                webView.clearFocus();
                webView.setFocusable(false);
                webView.setFocusableInTouchMode(false);
                hideKeyboard();
            } else {
                webView.setFocusable(true);
                webView.setFocusableInTouchMode(true);
            }
        }
        updateTouchLockButton();
    }

    private void updateTouchLockButton() {
        if (touchLockButton == null) return;
        touchLockButton.setImageResource(
                touchLocked ? R.drawable.ic_toolbar_lock : R.drawable.ic_toolbar_lock_open
        );
        touchLockButton.setContentDescription(
                touchLocked ? "Unlock touch input" : "Lock touch input"
        );
        touchLockButton.setBackground(toolbarButtonBackground(touchLocked));
    }

    private void hideKeyboard() {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            View target = getCurrentFocus();
            if (target == null) target = webView;
            if (target != null) manager.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
    }

    private CodespaceReconnectPolicy.Signal parseConnectionSignal(String result) {
        if (result == null) return CodespaceReconnectPolicy.Signal.HEALTHY;
        String normalized = result.toLowerCase();
        if (normalized.contains("offline")) {
            return CodespaceReconnectPolicy.Signal.OFFLINE_PAGE;
        }
        if (normalized.contains("disconnected")) {
            return CodespaceReconnectPolicy.Signal.VS_CODE_DISCONNECTED;
        }
        return CodespaceReconnectPolicy.Signal.HEALTHY;
    }

    private void startNetworkObservation() {
        connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        updateInitialNetworkState();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                updateValidatedNetwork(network);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                networkValidated = isValidated(capabilities);
            }

            @Override
            public void onLost(Network network) {
                updateInitialNetworkState();
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            networkCallbackRegistered = true;
        } catch (Throwable ignored) {
            networkCallbackRegistered = false;
        }
    }

    private void updateInitialNetworkState() {
        if (connectivityManager == null) {
            networkValidated = false;
            return;
        }
        try {
            Network active = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = active == null
                    ? null
                    : connectivityManager.getNetworkCapabilities(active);
            networkValidated = isValidated(capabilities);
        } catch (Throwable ignored) {
            networkValidated = false;
        }
    }

    private void updateValidatedNetwork(Network network) {
        if (connectivityManager == null || network == null) {
            networkValidated = false;
            return;
        }
        try {
            networkValidated = isValidated(connectivityManager.getNetworkCapabilities(network));
        } catch (Throwable ignored) {
            networkValidated = false;
        }
    }

    private boolean isValidated(NetworkCapabilities capabilities) {
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void maybeShowBackgroundSetup() {
        if (BackgroundReadiness.allReady(this)) return;
        boolean prompted = getPreferences(MODE_PRIVATE)
                .getBoolean(PREF_SETUP_PROMPTED_V102, false);
        if (prompted) return;

        getPreferences(MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_SETUP_PROMPTED_V102, true)
                .apply();
        if (webContainer != null) {
            webContainer.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) showBackgroundSetup();
            }, 600L);
        }
    }

    private void showBackgroundSetup() {
        if (isFinishing() || isDestroyed()) return;
        if (setupDialog != null && setupDialog.isShowing()) {
            setupDialog.dismiss();
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(4));

        TextView explanation = new TextView(this);
        explanation.setText(R.string.background_setup_explanation);
        explanation.setTextSize(14f);
        explanation.setTextColor(0xFF444444);
        explanation.setPadding(0, 0, 0, dp(12));
        content.addView(explanation);

        addReadinessRow(
                content,
                "Notifications",
                BackgroundReadiness.notificationStatus(this),
                "Allow",
                () -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        dismissSetupDialog();
                        requestPermissions(
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                REQUEST_NOTIFICATIONS
                        );
                    }
                }
        );

        addReadinessRow(
                content,
                "Battery optimization",
                BackgroundReadiness.batteryStatus(this),
                "Allow",
                () -> {
                    dismissSetupDialog();
                    openSettingsIntent(
                            BackgroundReadiness.batteryOptimizationIntent(this),
                            BackgroundReadiness.genericBatteryOptimizationIntent()
                    );
                }
        );

        addReadinessRow(
                content,
                "Unused app setting",
                BackgroundReadiness.unusedAppStatus(this),
                "App Info",
                () -> {
                    dismissSetupDialog();
                    Toast.makeText(
                            this,
                            "Disable ‘Pause app activity if unused’ or the equivalent setting.",
                            Toast.LENGTH_LONG
                    ).show();
                    openSettingsIntent(BackgroundReadiness.appDetailsIntent(this), null);
                }
        );

        TextView normalPermissions = new TextView(this);
        normalPermissions.setText(
                "Internet, network-state, foreground-service and wake-lock access are normal app permissions handled automatically by Android."
        );
        normalPermissions.setTextSize(12f);
        normalPermissions.setTextColor(0xFF666666);
        normalPermissions.setPadding(0, dp(12), 0, 0);
        content.addView(normalPermissions);

        setupDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.background_setup_title)
                .setView(content)
                .setNegativeButton("Close", null)
                .create();
        setupDialog.setOnDismissListener(dialog -> setupDialog = null);
        setupDialog.show();
    }

    private void addReadinessRow(
            LinearLayout parent,
            String title,
            BackgroundReadiness.Status status,
            String actionText,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15f);
        titleView.setTextColor(Color.BLACK);
        TextView statusView = new TextView(this);
        statusView.setText(readinessLabel(status));
        statusView.setTextSize(12f);
        statusView.setTextColor(status == BackgroundReadiness.Status.READY
                ? 0xFF178A3A : 0xFF9A5B00);
        textColumn.addView(titleView);
        textColumn.addView(statusView);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button actionButton = new Button(this);
        actionButton.setAllCaps(false);
        actionButton.setText(status == BackgroundReadiness.Status.READY ? "Ready" : actionText);
        actionButton.setEnabled(status != BackgroundReadiness.Status.READY);
        actionButton.setOnClickListener(v -> action.run());
        row.addView(actionButton, new LinearLayout.LayoutParams(dp(112), dp(48)));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private String readinessLabel(BackgroundReadiness.Status status) {
        if (status == BackgroundReadiness.Status.READY) return "Ready";
        if (status == BackgroundReadiness.Status.REVIEW) return "Review setting";
        return "Needs action";
    }

    private void dismissSetupDialog() {
        if (setupDialog != null && setupDialog.isShowing()) {
            setupDialog.dismiss();
        }
    }

    private void openSettingsIntent(Intent preferred, Intent fallback) {
        try {
            if (preferred != null && preferred.resolveActivity(getPackageManager()) != null) {
                startActivity(preferred);
                return;
            }
            if (fallback != null && fallback.resolveActivity(getPackageManager()) != null) {
                startActivity(fallback);
                return;
            }
            Toast.makeText(this, "System settings page is unavailable", Toast.LENGTH_LONG).show();
        } catch (Throwable error) {
            Toast.makeText(this, "Could not open system settings", Toast.LENGTH_LONG).show();
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
        boolean canBack = webView != null && webView.canGoBack();
        boolean canForward = webView != null && webView.canGoForward();
        if (backButton != null) {
            backButton.setEnabled(canBack);
            backButton.setAlpha(canBack ? 1f : 0.35f);
        }
        if (forwardButton != null) {
            forwardButton.setEnabled(canForward);
            forwardButton.setAlpha(canForward ? 1f : 0.35f);
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
    protected void onResume() {
        super.onResume();
        appForeground = true;
        reconnectHandler.removeCallbacks(reconnectProbe);
        reconnectHandler.postDelayed(reconnectProbe, CONNECTION_PROBE_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        appForeground = false;
        reconnectHandler.removeCallbacks(reconnectProbe);
        CookieManager.getInstance().flush();
        super.onPause();
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

        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(
                    this,
                    granted
                            ? "Keep Alive notifications allowed"
                            : "Notification permission is still disabled",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

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
        outState.putBoolean(STATE_TOUCH_LOCKED, touchLocked);
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
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
        dismissSetupDialog();

        if (networkCallbackRegistered && connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Throwable ignored) {
            }
        }
        networkCallbackRegistered = false;
        networkCallback = null;

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
