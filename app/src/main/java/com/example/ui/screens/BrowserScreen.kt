package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.StreamMedia
import com.example.player.PlayerViewModel
import com.example.sniffer.InjectedJs
import com.example.ui.components.DetectedStreamBanner
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.CinemaCardBorder
import com.example.ui.theme.CinemaDark
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collectLatest

private const val DEFAULT_URL = "https://filmemeu.vercel.app/"
private const val APP_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 FilmeMeuApp/1.0"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    playerViewModel: PlayerViewModel,
    onPlayStream: (StreamMedia) -> Unit,
    onNavigateToDetected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = playerViewModel.repository
    val context = LocalContext.current

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(DEFAULT_URL) }
    var inputUrl by remember { mutableStateOf(DEFAULT_URL) }
    var pageTitle by remember { mutableStateOf("FilmeMeu") }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    var tmdbKey by remember { mutableStateOf(InjectedJs.DEFAULT_TMDB_KEY) }
    var showKeyDialog by remember { mutableStateOf(false) }

    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val detectedList by repository.detectedStreams.collectAsState()
    val above15Count = remember(detectedList) { detectedList.count { it.isAbove15Min } }

    var latestBannerStream by remember { mutableStateOf<StreamMedia?>(null) }

    // Listen to repository movie alerts
    LaunchedEffect(Unit) {
        repository.newMovieAlert.collectLatest { stream ->
            latestBannerStream = stream
        }
    }

    // Listen to remote control and keyboard actions targeting the web player
    LaunchedEffect(webViewInstance) {
        playerViewModel.webKeyEvents.collectLatest { action ->
            when (action) {
                PlayerViewModel.WebKeyAction.PLAY_PAUSE -> {
                    webViewInstance?.evaluateJavascript(
                        "if(window.__toggleWebPlayerPlayPause) { window.__toggleWebPlayerPlayPause(); }",
                        null
                    )
                }
                PlayerViewModel.WebKeyAction.REWIND -> {
                    webViewInstance?.evaluateJavascript(
                        "if(window.__seekWebPlayer) { window.__seekWebPlayer(-10); }",
                        null
                    )
                }
                PlayerViewModel.WebKeyAction.FORWARD -> {
                    webViewInstance?.evaluateJavascript(
                        "if(window.__seekWebPlayer) { window.__seekWebPlayer(10); }",
                        null
                    )
                }
            }
        }
    }

    // Back handling: if web fullscreen is active, exit it. Otherwise handle webview back
    BackHandler {
        if (customFullscreenView != null) {
            customViewCallback?.onCustomViewHidden()
            customFullscreenView = null
            customViewCallback = null
        } else if (canGoBack && webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackPressTime = now
                Toast.makeText(context, "Pressione voltar novamente para sair do aplicativo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Toolbar
            Surface(
                color = CinemaDark,
                border = BorderStroke(1.dp, CinemaCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (webViewInstance?.canGoBack() == true) {
                                    webViewInstance?.goBack()
                                }
                            },
                            enabled = canGoBack,
                            modifier = Modifier.size(36.dp).testTag("web_btn_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = if (canGoBack) TextPrimary else TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (webViewInstance?.canGoForward() == true) {
                                    webViewInstance?.goForward()
                                }
                            },
                            enabled = canGoForward,
                            modifier = Modifier.size(36.dp).testTag("web_btn_forward")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Avançar",
                                tint = if (canGoForward) TextPrimary else TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                webViewInstance?.loadUrl(DEFAULT_URL)
                                inputUrl = DEFAULT_URL
                            },
                            modifier = Modifier.size(36.dp).testTag("web_btn_home")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Início",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Address bar / Search Input
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .padding(horizontal = 4.dp)
                                .testTag("web_url_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = CinemaCardBorder,
                                focusedContainerColor = CinemaSurface,
                                unfocusedContainerColor = CinemaSurface,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextSecondary
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                val target = if (inputUrl.startsWith("http://") || inputUrl.startsWith("https://")) {
                                    inputUrl
                                } else {
                                    "https://$inputUrl"
                                }
                                webViewInstance?.loadUrl(target)
                            })
                        )

                        // TMDB Key Button
                        IconButton(
                            onClick = { showKeyDialog = true },
                            modifier = Modifier.size(36.dp).testTag("web_btn_tmdb_key")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Chave TMDB",
                                tint = NeonAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.size(36.dp).testTag("web_btn_reload")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Recarregar",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Streams Detected Badge Button
                        BadgedBox(
                            badge = {
                                if (detectedList.isNotEmpty()) {
                                    Badge(
                                        containerColor = if (above15Count > 0) NeonAmber else NeonCyan,
                                        contentColor = Color.Black
                                    ) {
                                        Text(
                                            text = detectedList.size.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.padding(start = 2.dp, end = 4.dp)
                        ) {
                            IconButton(
                                onClick = onNavigateToDetected,
                                modifier = Modifier.size(36.dp).testTag("btn_view_streams")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = "Ver Streams",
                                    tint = if (above15Count > 0) NeonAmber else NeonCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Page Loading Progress Bar
                    if (isLoading && pageProgress < 1f) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { pageProgress },
                            color = NeonCyan,
                            trackColor = CinemaSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                        )
                    }
                }
            }

            // Webview Content Area + Floating Detected Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = APP_USER_AGENT
                            }

                            // JavaScript Native Bridge - Thread-safe
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onVideoFound(url: String?, title: String?, durationSec: Long) {
                                    try {
                                        if (!url.isNullOrBlank()) {
                                            repository.onStreamCandidate(
                                                url = url,
                                                pageTitle = title ?: pageTitle,
                                                pageUrl = currentUrl,
                                                initialDurationSec = durationSec,
                                                userAgent = APP_USER_AGENT
                                            )
                                        }
                                    } catch (e: Throwable) {
                                        // Ignore
                                    }
                                }
                            }, "AndroidBridge")

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    try {
                                        val reqUrl = request?.url?.toString() ?: return false
                                        val lower = reqUrl.lowercase()

                                        if (lower.contains(".m3u8") || lower.contains(".mp4")) {
                                            repository.onStreamCandidate(
                                                url = reqUrl,
                                                pageTitle = pageTitle,
                                                pageUrl = currentUrl,
                                                initialDurationSec = 0L,
                                                userAgent = APP_USER_AGENT
                                            )
                                            return true
                                        }

                                        if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                            return false
                                        }

                                        return try {
                                            val intent = Intent.parseUri(reqUrl, Intent.URI_INTENT_SCHEME)
                                            view?.context?.startActivity(intent)
                                            true
                                        } catch (e: Exception) {
                                            true
                                        }
                                    } catch (e: Exception) {
                                        return false
                                    }
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    try {
                                        val reqUrl = request?.url?.toString()
                                        if (reqUrl != null) {
                                            repository.onStreamCandidate(
                                                url = reqUrl,
                                                pageTitle = pageTitle,
                                                pageUrl = currentUrl,
                                                initialDurationSec = 0L,
                                                userAgent = APP_USER_AGENT
                                            )
                                        }
                                    } catch (e: Throwable) {
                                        // Ignore
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    if (url != null) {
                                        currentUrl = url
                                        inputUrl = url
                                    }
                                    canGoBack = canGoBack()
                                    canGoForward = canGoForward()
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    canGoBack = canGoBack()
                                    canGoForward = canGoForward()
                                    try {
                                        view?.evaluateJavascript(InjectedJs.getSnifferScript(tmdbKey), null)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress / 100f
                                    if (newProgress == 100) {
                                        isLoading = false
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        pageTitle = title
                                    }
                                }

                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    customFullscreenView = view
                                    customViewCallback = callback
                                }

                                override fun onHideCustomView() {
                                    customViewCallback?.onCustomViewHidden()
                                    customFullscreenView = null
                                    customViewCallback = null
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    return false
                                }
                            }

                            loadUrl(DEFAULT_URL)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Detected Movie Banner at the bottom of the browser
                DetectedStreamBanner(
                    stream = latestBannerStream,
                    onPlay = { stream ->
                        latestBannerStream = null
                        onPlayStream(stream)
                    },
                    onDismiss = { latestBannerStream = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
        }

        // HTML5 Video Native Fullscreen View Overlay (when web player goes fullscreen)
        if (customFullscreenView != null) {
            AndroidView(
                factory = { _ ->
                    FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        addView(
                            customFullscreenView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // TMDB Key Dialog
    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            containerColor = CinemaDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chave da API do TMDB",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Esta chave é necessária para liberar o catálogo do FilmeMeu e carregar as capas e títulos:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tmdbKey,
                        onValueChange = { tmdbKey = it },
                        label = { Text("Chave TMDB") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonAmber,
                            unfocusedBorderColor = CinemaCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_tmdb_key_dialog")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        webViewInstance?.evaluateJavascript(
                            InjectedJs.getTmdbAutoFillScript(tmdbKey.trim()),
                            null
                        )
                        Toast.makeText(context, "Chave TMDB inserida no sistema!", Toast.LENGTH_SHORT).show()
                        showKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonAmber,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("btn_save_tmdb_dialog")
                ) {
                    Text("Salvar Chave", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog = false }) {
                    Text("Fechar", color = TextSecondary)
                }
            }
        )
    }
}
