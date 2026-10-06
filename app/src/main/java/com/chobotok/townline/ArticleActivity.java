package com.chobotok.townline;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Simple in-app article reader. */
public class ArticleActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        String title = getIntent().getStringExtra("title");
        if (title != null) setTitle(title);

        web = findViewById(R.id.articleView);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        String url = getIntent().getStringExtra("url");
        if (url != null) web.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
