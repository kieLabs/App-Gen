package com.web2apk.template;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // Dynamically injected by GitHub Actions workflow_dispatch
    private static final String TARGET_URL = "__TARGET_URL__";
    private static final boolean ENABLE_FULLSCREEN = __FLAG_FULLSCREEN__;
    private static final boolean ENABLE_WEBRTC = __FLAG_WEBRTC__;
    private static final boolean ENABLE_FILE_UPLOADS = __FLAG_FILE_UPLOADS__;
    private static final boolean ENABLE_PULL_REFRESH = __FLAG_PULL_REFRESH__;

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ValueCallback<Uri[]> fileUploadCallback;
    private PermissionRequest pendingWebRtcRequest;
    private String lastFailingUrl = TARGET_URL;
    private boolean isShowingOfflinePage = false;

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (fileUploadCallback == null) return;
            Uri[] results = null;
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                String dataString = result.getData().getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                } else if (result.getData().getClipData() != null) {
                    int count = result.getData().getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = result.getData().getClipData().getItemAt(i).getUri();
                    }
                }
            }
            fileUploadCallback.onReceiveValue(results);
            fileUploadCallback = null;
        });

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ENABLE_FULLSCREEN) {
            applyFullscreenMode();
        }

        // Build programmatic layout so no external layout XML is required
        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(Color.parseColor("#06080E"));

        swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#00F3FF"),
            Color.parseColor("#FF007F")
        );
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.parseColor("#0D111A"));
        swipeRefreshLayout.setEnabled(ENABLE_PULL_REFRESH);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#06080E"));

        swipeRefreshLayout.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        rootContainer.addView(swipeRefreshLayout, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(rootContainer);

        configureWebViewSettings();
        configureWebViewClients();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isShowingOfflinePage) {
                retryConnection();
            } else {
                webView.reload();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack() && !isShowingOfflinePage) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });

        if (isNetworkAvailable()) {
            webView.loadUrl(TARGET_URL);
        } else {
            renderCustomOfflineScreen(TARGET_URL);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebViewSettings() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new OfflineBridge(), "CyberOfflineBridge");
    }

    private void configureWebViewClients() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (!url.startsWith("data:text/html")) {
                    isShowingOfflinePage = false;
                    lastFailingUrl = url;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    lastFailingUrl = request.getUrl().toString();
                    renderCustomOfflineScreen(lastFailingUrl);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                    startActivity(intent);
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (!ENABLE_WEBRTC) {
                    request.deny();
                    return;
                }
                runOnUiThread(() -> handleWebRtcPermissionRequest(request));
            }

            @Override
            public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
            ) {
                if (!ENABLE_FILE_UPLOADS) {
                    return false;
                }
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                try {
                    fileChooserLauncher.launch(Intent.createChooser(intent, "Select File"));
                } catch (Exception e) {
                    fileUploadCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimeType);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    request.addRequestHeader("cookie", cookies);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType)
                    );
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(request);
                    }
                } catch (Exception e) {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                }
            }
        });
    }

    private void handleWebRtcPermissionRequest(PermissionRequest request) {
        List<String> neededPermissions = new ArrayList<>();
        for (String res : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(Manifest.permission.CAMERA);
                }
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(Manifest.permission.RECORD_AUDIO);
                }
            }
        }

        if (neededPermissions.isEmpty()) {
            request.grant(request.getResources());
        } else {
            pendingWebRtcRequest = request;
            ActivityCompat.requestPermissions(
                this,
                neededPermissions.toArray(new String[0]),
                PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && pendingWebRtcRequest != null) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                pendingWebRtcRequest.grant(pendingWebRtcRequest.getResources());
            } else {
                pendingWebRtcRequest.deny();
            }
            pendingWebRtcRequest = null;
        }
    }

    private void renderCustomOfflineScreen(String failedUrl) {
        isShowingOfflinePage = true;
        String offlineHtml = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1.0'>"
            + "<style>"
            + "body{margin:0;padding:24px;background:#06080e;color:#e6f1ff;font-family:sans-serif;"
            + "display:flex;align-items:center;justify-content:center;min-height:90vh;text-align:center;}"
            + ".card{max-width:420px;background:#0d111a;border:1px solid #00f3ff;border-radius:14px;"
            + "padding:32px 24px;box-shadow:0 0 25px rgba(0,243,255,0.25);}"
            + ".icon{font-size:48px;color:#ff007f;margin-bottom:12px;}"
            + "h1{font-size:20px;letter-spacing:1.5px;text-transform:uppercase;color:#00f3ff;margin:0 0 12px;}"
            + "p{font-size:14px;color:#7d8da6;line-height:1.6;margin:0 0 24px;}"
            + "button{background:linear-gradient(90deg,#00f3ff,#00ff9d);color:#04060a;border:none;"
            + "padding:14px 28px;border-radius:8px;font-weight:800;font-size:14px;letter-spacing:1px;"
            + "text-transform:uppercase;cursor:pointer;box-shadow:0 0 15px rgba(0,243,255,0.4);}"
            + "</style></head><body>"
            + "<div class='card'>"
            + "<div class='icon'>&#9888;</div>"
            + "<h1>Connection Offline</h1>"
            + "<p>Unable to establish an encrypted link to the application server. Verify your Wi-Fi or cellular data signal and try again.</p>"
            + "<button onclick='CyberOfflineBridge.retry()'>Reconnect Now</button>"
            + "</div></body></html>";

        webView.loadDataWithBaseURL(null, offlineHtml, "text/html", "UTF-8", null);
    }

    private void retryConnection() {
        runOnUiThread(() -> {
            if (isNetworkAvailable()) {
                isShowingOfflinePage = false;
                webView.loadUrl(lastFailingUrl != null ? lastFailingUrl : TARGET_URL);
            } else {
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                renderCustomOfflineScreen(lastFailingUrl);
            }
        });
    }

    public class OfflineBridge {
        @JavascriptInterface
        public void retry() {
            retryConnection();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return cap != null && (
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    private void applyFullscreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }
}
