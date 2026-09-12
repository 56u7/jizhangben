package com.jizhang.jizhangben;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingFileName;
    private String pendingMime;
    private String pendingContent;
    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_CREATE_DOC = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> result.confirm())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> result.confirm())
                        .setNegativeButton(getString(R.string.dialog_cancel), (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePath, FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = filePath;
                Intent intent = fileChooserParams.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void saveFile(final String filename, final String mime, final String content) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pendingFileName = filename;
                    pendingMime = mime;
                    pendingContent = content;
                    String baseMime = "text/plain";
                    if (mime != null && mime.indexOf(';') > 0) {
                        baseMime = mime.substring(0, mime.indexOf(';')).trim();
                    } else if (mime != null && mime.length() > 0) {
                        baseMime = mime;
                    }
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(baseMime);
                    intent.putExtra(Intent.EXTRA_TITLE, filename);
                    try {
                        startActivityForResult(intent, REQ_CREATE_DOC);
                    } catch (Exception e) {
                        pendingFileName = null;
                        pendingMime = null;
                        pendingContent = null;
                        Toast.makeText(MainActivity.this, "无法打开保存窗口", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void moveTaskToBack() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.moveTaskToBack(true);
                }
            });
        }

        @JavascriptInterface
        public void setCaptureEnabled(final boolean enabled) {
            CaptureStore.setEnabled(MainActivity.this, enabled);
        }

        @JavascriptInterface
        public String takeCaptures() {
            return CaptureStore.takeQueue(MainActivity.this);
        }

        @JavascriptInterface
        public boolean isNotificationAccessGranted() {
            try {
                String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
                String component = new ComponentName(MainActivity.this, WechatCaptureService.class).flattenToString();
                return flat != null && flat.contains(component);
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean getCaptureEnabled() {
            return CaptureStore.isEnabled(MainActivity.this);
        }

        @JavascriptInterface
        public String getCaptureDiagnostic() {
            return CaptureStore.getDiagnostic(MainActivity.this);
        }

        @JavascriptInterface
        public void openNotificationAccessSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "无法打开通知使用权设置", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            if (filePathCallback != null) {
                Uri result = (data == null || resultCode != RESULT_OK) ? null : data.getData();
                filePathCallback.onReceiveValue(result == null ? null : new Uri[]{result});
                filePathCallback = null;
            }
        } else if (requestCode == REQ_CREATE_DOC) {
            if (resultCode == RESULT_OK && data != null && pendingContent != null) {
                try {
                    OutputStream os = getContentResolver().openOutputStream(data.getData());
                    if (os != null) {
                        OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                        writer.write(pendingContent);
                        writer.flush();
                        writer.close();
                        Toast.makeText(this, "文件已保存", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
                }
            }
            pendingFileName = null;
            pendingMime = null;
            pendingContent = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.getUrl() != null) {
            webView.evaluateJavascript(
                    "if (window.__dispatchBack) window.__dispatchBack();",
                    null
            );
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
