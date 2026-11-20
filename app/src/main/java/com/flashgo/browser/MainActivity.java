package com.flashgo.browser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private FrameLayout webContainer;
    private EditText urlInput;
    private ProgressBar progressBar;
    private Button tabCountBtn;

    private final List<WebView> tabs = new ArrayList<>();
    private final List<Boolean> tabIncognito = new ArrayList<>();
    private int currentTab = -1;

    private boolean adBlockEnabled = true;
    private boolean httpsOnlyEnabled = true;
    private boolean desktopMode = false;

    private final List<String> adDomains = Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "google-analytics.com",
            "adnxs.com", "facebook.com/tr", "taboola.com", "outbrain.com", "scorecardresearch.com"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F1117"));

        // Top Address Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(12, 12, 12, 12);
        topBar.setBackgroundColor(Color.parseColor("#181B24"));
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        urlInput = new EditText(this);
        urlInput.setHint("Search Google or URL...");
        urlInput.setHintTextColor(Color.GRAY);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setSingleLine(true);
        urlInput.setTextSize(14);
        urlInput.setPadding(24, 14, 24, 14);
        urlInput.setBackgroundColor(Color.parseColor("#090A0F"));
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        urlInput.setLayoutParams(urlParams);
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadUrl(urlInput.getText().toString());
                return true;
            }
            return false;
        });

        Button goBtn = makeSmallBtn("Go", "#3B82F6", v -> loadUrl(urlInput.getText().toString()));
        tabCountBtn = makeSmallBtn("1", "#252A37", v -> showTabSwitcher());
        Button fireBtn = makeSmallBtn("🔥", "#7F1D1D", v -> fireBurn());
        Button menuBtn = makeSmallBtn("⋮", "#252A37", v -> showFeaturesMenu());

        topBar.addView(urlInput);
        topBar.addView(goBtn);
        topBar.addView(tabCountBtn);
        topBar.addView(fireBtn);
        topBar.addView(menuBtn);

        // Progress Bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8));

        // Web Container
        webContainer = new FrameLayout(this);
        webContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom Navigation Bar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#181B24"));
        bottomBar.setPadding(8, 8, 8, 8);
        bottomBar.setGravity(Gravity.CENTER);

        bottomBar.addView(makeNavBtn("◀", v -> { if (activeWeb() != null && activeWeb().canGoBack()) activeWeb().goBack(); }));
        bottomBar.addView(makeNavBtn("▶", v -> { if (activeWeb() != null && activeWeb().canGoForward()) activeWeb().goForward(); }));
        bottomBar.addView(makeNavBtn("↻", v -> { if (activeWeb() != null) activeWeb().reload(); }));
        bottomBar.addView(makeNavBtn("🏠", v -> loadUrl("https://www.google.com")));
        bottomBar.addView(makeNavBtn("➕", v -> createNewTab("https://www.google.com", false)));

        root.addView(topBar);
        root.addView(progressBar);
        root.addView(webContainer);
        root.addView(bottomBar);

        setContentView(root);
        createNewTab("https://www.google.com", false);
    }

    private Button makeSmallBtn(String text, String bgHex, android.view.View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setBackgroundColor(Color.parseColor(bgHex));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(110, 95);
        lp.setMargins(6, 0, 0, 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(listener);
        return b;
    }

    private Button makeNavBtn(String text, android.view.View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        b.setOnClickListener(listener);
        return b;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createNewTab(String url, boolean isPrivate) {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(!isPrivate);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        if (isPrivate) {
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
            CookieManager.getInstance().setAcceptCookie(false);
        } else {
            CookieManager.getInstance().setAcceptCookie(true);
        }

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString().toLowerCase();
                if (adBlockEnabled) {
                    for (String domain : adDomains) {
                        if (reqUrl.contains(domain)) {
                            return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String nextUrl = request.getUrl().toString();
                if (httpsOnlyEnabled && nextUrl.startsWith("http://")) {
                    view.loadUrl(nextUrl.replace("http://", "https://"));
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                if (activeWeb() == view) urlInput.setText(loadedUrl);
                // Hide Cookie Banners
                view.evaluateJavascript(
                        "document.querySelectorAll('[id*=\"cookie\" i],[class*=\"cookie\" i],[id*=\"consent\" i]').forEach(e=>e.style.display='none');",
                        null
                );
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (activeWeb() == view) {
                    progressBar.setProgress(newProgress);
                }
            }
        });

        wv.setDownloadListener((dlUrl, userAgent, contentDisposition, mimeType, contentLength) -> {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(dlUrl));
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(dlUrl, contentDisposition, mimeType));
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(req);
            Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show();
        });

        tabs.add(wv);
        tabIncognito.add(isPrivate);
        switchToTab(tabs.size() - 1);
        loadUrl(url);
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentTab = index;
        webContainer.removeAllViews();
        WebView wv = tabs.get(index);
        webContainer.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        urlInput.setText(wv.getUrl() != null ? wv.getUrl() : "");
        tabCountBtn.setText(String.valueOf(tabs.size()));
    }

    private WebView activeWeb() {
        if (currentTab >= 0 && currentTab < tabs.size()) return tabs.get(currentTab);
        return null;
    }

    private void loadUrl(String query) {
        if (activeWeb() == null || query == null) return;
        String q = query.trim();
        if (q.isEmpty()) return;
        if (q.contains(".") && !q.contains(" ")) {
            if (!q.startsWith("http")) q = (httpsOnlyEnabled ? "https://" : "http://") + q;
        } else {
            q = "https://www.google.com/search?q=" + Uri.encode(q);
        }
        activeWeb().loadUrl(q);
    }

    private void showTabSwitcher() {
        String[] titles = new String[tabs.size() + 2];
        for (int i = 0; i < tabs.size(); i++) {
            String prefix = tabIncognito.get(i) ? "🕵️ " : "🌐 ";
            String t = tabs.get(i).getTitle();
            titles[i] = prefix + (t != null && !t.isEmpty() ? t : "Tab " + (i + 1));
        }
        titles[tabs.size()] = "➕ New Normal Tab";
        titles[tabs.size() + 1] = "🕵️ New Private Tab";

        new AlertDialog.Builder(this)
                .setTitle("Open Tabs (" + tabs.size() + ")")
                .setItems(titles, (d, which) -> {
                    if (which == tabs.size()) createNewTab("https://www.google.com", false);
                    else if (which == tabs.size() + 1) createNewTab("https://duckduckgo.com", true);
                    else switchToTab(which);
                })
                .setNegativeButton("Close Current", (d, w) -> closeCurrentTab())
                .show();
    }

    private void closeCurrentTab() {
        if (tabs.size() <= 1) {
            loadUrl("https://www.google.com");
            return;
        }
        tabs.get(currentTab).destroy();
        tabs.remove(currentTab);
        tabIncognito.remove(currentTab);
        switchToTab(Math.max(0, currentTab - 1));
    }

    private void fireBurn() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        for (WebView wv : tabs) {
            wv.clearCache(true);
            wv.clearHistory();
            wv.destroy();
        }
        tabs.clear();
        tabIncognito.clear();
        createNewTab("https://www.google.com", false);
        Toast.makeText(this, "🔥 All Tabs, Cookies & History Burned!", Toast.LENGTH_SHORT).show();
    }

    private void showFeaturesMenu() {
        String[] options = {
                "🛡️ Ad-Blocker: " + (adBlockEnabled ? "ON" : "OFF"),
                "🔒 HTTPS-Only: " + (httpsOnlyEnabled ? "ON" : "OFF"),
                "💻 Desktop Site: " + (desktopMode ? "ON" : "OFF"),
                "📖 Reader View (Clean Article)",
                "⭐ Save Bookmark",
                "📚 View Bookmarks",
                "📝 Quick Notes"
        };

        new AlertDialog.Builder(this)
                .setTitle("FlashGo Tools")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        adBlockEnabled = !adBlockEnabled;
                        Toast.makeText(this, "Ad-Blocker: " + (adBlockEnabled ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        httpsOnlyEnabled = !httpsOnlyEnabled;
                        Toast.makeText(this, "HTTPS-Only: " + (httpsOnlyEnabled ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                    } else if (which == 2 && activeWeb() != null) {
                        desktopMode = !desktopMode;
                        String ua = desktopMode
                                ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
                                : null;
                        activeWeb().getSettings().setUserAgentString(ua);
                        activeWeb().reload();
                    } else if (which == 3 && activeWeb() != null) {
                        activeWeb().evaluateJavascript(
                                "document.body.style.background='#18181b';document.body.style.color='#e4e4e7';document.body.style.fontSize='18px';document.querySelectorAll('nav,header,footer,aside,iframe').forEach(e=>e.remove());",
                                null
                        );
                    } else if (which == 4 && activeWeb() != null) {
                        SharedPreferences sp = getSharedPreferences("fg_prefs", Context.MODE_PRIVATE);
                        String old = sp.getString("bm", "");
                        sp.edit().putString("bm", old + activeWeb().getUrl() + "\n").apply();
                        Toast.makeText(this, "⭐ Bookmark Saved!", Toast.LENGTH_SHORT).show();
                    } else if (which == 5) {
                        SharedPreferences sp = getSharedPreferences("fg_prefs", Context.MODE_PRIVATE);
                        String[] bms = sp.getString("bm", "https://www.google.com\n").trim().split("\n");
                        new AlertDialog.Builder(this).setTitle("Saved Bookmarks").setItems(bms, (d2, i) -> loadUrl(bms[i])).show();
                    } else if (which == 6) {
                        SharedPreferences sp = getSharedPreferences("fg_prefs", Context.MODE_PRIVATE);
                        EditText noteBox = new EditText(this);
                        noteBox.setText(sp.getString("notes", ""));
                        new AlertDialog.Builder(this)
                                .setTitle("📝 Quick Notes")
                                .setView(noteBox)
                                .setPositiveButton("Save", (d2, w) -> sp.edit().putString("notes", noteBox.getText().toString()).apply())
                                .show();
                    }
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        if (activeWeb() != null && activeWeb().canGoBack()) {
            activeWeb().goBack();
        } else {
            super.onBackPressed();
        }
    }
}
