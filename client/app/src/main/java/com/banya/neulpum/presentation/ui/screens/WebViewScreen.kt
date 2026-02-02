package com.banya.neulpum.presentation.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WebViewScreen(
    url: String,
    title: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 웹뷰 (전체화면)
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    
                    // 웹뷰 설정
                    val webSettings = settings
                    webSettings.javaScriptEnabled = true
                    webSettings.domStorageEnabled = true
                    webSettings.loadWithOverviewMode = true
                    webSettings.useWideViewPort = true
                    webSettings.allowFileAccess = true
                    webSettings.allowContentAccess = true
                    webSettings.setSupportZoom(true)
                    webSettings.builtInZoomControls = true
                    webSettings.displayZoomControls = false
                    
                    // User-Agent 설정 (모바일 브라우저로 인식하도록)
                    webSettings.userAgentString = 
                        "Mozilla/5.0 (Linux; Android 10; Pixel 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    
                    // 쿠키 설정
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 상단 백버튼 (오버레이)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                modifier = Modifier.align(Alignment.TopStart),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
