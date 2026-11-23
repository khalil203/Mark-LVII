package com.marklvii.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.SharedPreferences
import android.net.http.SslError
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private val key = "dashboard_url"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("marklvii", MODE_PRIVATE)
        webView = findViewById(R.id.webview)
        val s: WebSettings = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.loadsImagesAutomatically = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }
        val url = prefs.getString(key, null)
        if (url.isNullOrBlank()) promptUrl() else webView.loadUrl(url)
    }

    private fun promptUrl() {
        val input = android.widget.EditText(this).apply {
            hint = "https://192.168.x.x:8001"
            setText(prefs.getString(key, "https://"))
        }
        AlertDialog.Builder(this)
            .setTitle("Connect to Mark LVII desktop")
            .setMessage("Enter your desktop's dashboard URL (shown by Mark LVII as a QR code).")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val u = input.text.toString().trim()
                prefs.edit().putString(key, u).apply()
                webView.loadUrl(u)
            }
            .setNegativeButton("Use demo page") { _, _ ->
                webView.loadUrl("https://khalil203.github.io/Mark-LVII/")
            }
            .setCancelable(false)
            .show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
