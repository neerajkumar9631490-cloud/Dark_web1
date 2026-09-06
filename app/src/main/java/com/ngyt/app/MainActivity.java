package com.ngyt.app;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * NGYT single-activity app.
 *
 * <p>Loads https://m.youtube.com in a full-screen WebView and injects
 * a JavaScript ad-blocker (assets/adblock.js) to skip/hide video ads,
 * banners and overlays. No other features are included by design.
 */
public class MainActivity extends AppCompatActivity {

    /** Mobile YouTube URL loaded on startup. */
    private static final String HOME_URL = "https://m.youtube.com";

    private WebView webView;
    /** Cached ad-block JS read once from assets. */
    private String adBlockJs = "";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen: no title bar, hide status bar.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        // Short disclaimer shown once at startup.
        Toast.makeText(
                this,
                "NGYT loads YouTube in a WebView. Ads are blocked locally for a cleaner view.",
                Toast.LENGTH_LONG).show();

        // Load the ad-block script once so every injection is cheap.
        adBlockJs = loadAssetText("adblock.js");

        webView = findViewById(R.id.webview);
        configureWebView(webView);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    /**
     * Applies the WebView settings needed for modern YouTube mobile
     * plus full-screen / immersive behaviour.
     */
    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true); // Required for YouTube + ad-block injection.
        settings.setDomStorageEnabled(true); // Required for YouTube login/state.
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        // Standard mobile UA keeps m.youtube.com layout; do not override.
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest request) {
                // Keep all navigation inside this WebView.
                return false;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView w, String url) {
                // Old-API fallback: keep navigation inside the WebView.
                return false;
            }

            @Override
            public void onPageStarted(WebView w, String url, Bitmap favicon) {
                super.onPageStarted(w, url, favicon);
                injectAdBlock(w);
            }

            @Override
            public void onPageFinished(WebView w, String url) {
                super.onPageFinished(w, url);
                injectAdBlock(w);
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView w, int newProgress) {
                // YouTube is a SPA: re-inject while navigating between videos.
                if (newProgress > 50) {
                    injectAdBlock(w);
                }
            }
        });
    }

    /**
     * Injects the cached ad-block JavaScript into the page.
     * Safe to call repeatedly; the script guards against double-install.
     */
    private void injectAdBlock(WebView view) {
        if (view == null || adBlockJs == null || adBlockJs.isEmpty()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(adBlockJs, null);
        } else {
            // Min SDK 21 is >= KITKAT, kept for completeness.
            // noinspection deprecation
            view.loadUrl("javascript:" + adBlockJs);
        }
    }

    /** Reads a text file from src/main/assets into a String. */
    private String loadAssetText(String assetName) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getAssets().open(assetName);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            // Leave empty; inject becomes a no-op so the app still runs.
            e.printStackTrace();
        }
        // Wrap in an IIFE-friendly payload without breaking evaluateJavascript.
        // The asset file itself is already a self-contained IIFE.
        return sb.toString();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onBackPressed() {
        // Back navigates WebView history first, exits only when empty.
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
