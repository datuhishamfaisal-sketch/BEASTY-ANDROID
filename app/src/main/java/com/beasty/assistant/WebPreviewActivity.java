package com.beasty.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WebPreviewActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String html = getIntent().getStringExtra("website_html");
        String name = getIntent().getStringExtra("website_name");
        if (html == null || html.trim().isEmpty()) {
            Toast.makeText(this, "Tiada website untuk dipratonton.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 13, 27));
        TextView title = new TextView(this);
        title.setText("BEASTY WEB PREVIEW  |  " + (name == null ? "Website" : name));
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(16, 16, 16, 16);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        Button close = new Button(this);
        close.setText("CLOSE PREVIEW");
        close.setOnClickListener(v -> finish());
        root.addView(close, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        webView.loadDataWithBaseURL("https://beasty.local/", html, "text/html", "UTF-8", null);
    }
}
