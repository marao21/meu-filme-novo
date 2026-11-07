package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import com.example.update.BaixarAtualizacao
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val DEFAULT_URL = "https://filmemeu.vercel.app/"
private const val APP_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"

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
    val coroutineScope = rememberCoroutineScope()

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

    var isUserInMovie by remember { mutableStateOf(false) }
    var activeMovieTitle by remember { mutableStateOf<String?>(null) }
    var latestBannerStream by remember { mutableStateOf<StreamMedia?>(null) }
    var userRequestedWatch by remember { mutableStateOf(false) }

    // Native TV Box On-Screen Search Keyboard
    var showTvSearchKeyboard by remember { mutableStateOf(false) }
    var tvSearchQuery by remember { mutableStateOf("") }

    // Virtual Mouse for TV Box Remote Control
    var isMouseModeActive by remember { mutableStateOf(false) }
    var cursorX by remember { mutableFloatStateOf(640f) }
    var cursorY by remember { mutableFloatStateOf(360f) }
    var isClicking by remember { mutableStateOf(false) }
    var mouseHudMessage by remember { mutableStateOf<String?>(null) }

    val vmMouseMode by playerViewModel.isMouseModeEnabled.collectAsState()
    LaunchedEffect(vmMouseMode) {
        if (isMouseModeActive != vmMouseMode) {
            isMouseModeActive = vmMouseMode
        }
    }

    LaunchedEffect(isUserInMovie) {
        if (isUserInMovie) {
            isMouseModeActive = true
            playerViewModel.setMouseModeEnabled(true)
            mouseHudMessage = "Modo Mouse ATIVADO: Use as setas do controle para mover e OK para clicar"
            coroutineScope.launch {
                kotlinx.coroutines.delay(3500)
                if (mouseHudMessage?.startsWith("Modo Mouse") == true) {
                    mouseHudMessage = null
                }
            }
        }
    }

    val dispatchHoverAt: (Float, Float) -> Unit = { x, y ->
        webViewInstance?.let { wv ->
            try {
                val downTime = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_HOVER_MOVE,
                    1,
                    arrayOf(MotionEvent.PointerProperties().apply {
                        id = 0
                        toolType = MotionEvent.TOOL_TYPE_MOUSE
                    }),
                    arrayOf(MotionEvent.PointerCoords().apply {
                        this.x = x
                        this.y = y
                        pressure = 1f
                        size = 1f
                    }),
                    0,
                    0,
                    1f,
                    1f,
                    0,
                    0,
                    InputDevice.SOURCE_MOUSE,
                    0
                )
                wv.dispatchGenericMotionEvent(event)
                event.recycle()
            } catch (e: Exception) {}
            wv.evaluateJavascript("if(window.__moveVirtualCursor) window.__moveVirtualCursor($x, $y);", null)
        }
    }

    val dispatchClickAt: (Float, Float) -> Unit = { x, y ->
        webViewInstance?.let { wv ->
            try {
                val downTime = SystemClock.uptimeMillis()
                val downEvent = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    1,
                    arrayOf(MotionEvent.PointerProperties().apply {
                        id = 0
                        toolType = MotionEvent.TOOL_TYPE_MOUSE
                    }),
                    arrayOf(MotionEvent.PointerCoords().apply {
                        this.x = x
                        this.y = y
                        pressure = 1f
                        size = 1f
                    }),
                    0,
                    MotionEvent.BUTTON_PRIMARY,
                    1f,
                    1f,
                    0,
                    0,
                    InputDevice.SOURCE_MOUSE,
                    0
                )
                wv.dispatchTouchEvent(downEvent)
                downEvent.recycle()

                wv.postDelayed({
                    try {
                        val upTime = SystemClock.uptimeMillis()
                        val upEvent = MotionEvent.obtain(
                            downTime,
                            upTime,
                            MotionEvent.ACTION_UP,
                            1,
                            arrayOf(MotionEvent.PointerProperties().apply {
                                id = 0
                                toolType = MotionEvent.TOOL_TYPE_MOUSE
                            }),
                            arrayOf(MotionEvent.PointerCoords().apply {
                                this.x = x
                                this.y = y
                                pressure = 1f
                                size = 1f
                            }),
                            0,
                            0,
                            1f,
                            1f,
                            0,
                            0,
                            InputDevice.SOURCE_MOUSE,
                            0
                        )
                        wv.dispatchTouchEvent(upEvent)
                        upEvent.recycle()
                    } catch (e: Exception) {}
                }, 60)
            } catch (e: Exception) {}
            wv.evaluateJavascript("if(window.__clickVirtualCursor) window.__clickVirtualCursor($x, $y);", null)
        }
    }

    // Helper for 1-click direct return to catalog
    val returnToCatalogInOneClick: () -> Unit = {
        playerViewModel.stopPlayback()
        isUserInMovie = false
        userRequestedWatch = false
        activeMovieTitle = null
        latestBannerStream = null
        repository.clearDetected()
        webViewInstance?.evaluateJavascript(
            """
            (function() {
                try {
                    if (window.__goToCatalog) {
                        window.__goToCatalog();
                    } else {
                        localStorage.removeItem('bvd:place');
                        localStorage.setItem('bvd:visitante', '1');
                        var btnVis = document.getElementById('btn-visitante');
                        if (btnVis) btnVis.click();
                        var lp = document.getElementById('login-page');
                        if (lp) lp.classList.add('hidden');
                        var dt = document.getElementById('detalhe');
                        if (dt) dt.classList.add('hidden');
                        var sf = document.getElementById('site-frame');
                        if (sf) {
                            sf.classList.add('hidden');
                            var ifr = sf.querySelector('iframe');
                            if (ifr) ifr.src = 'about:blank';
                        }
                        var np = document.getElementById('nosso-player');
                        if (np) {
                            np.classList.add('hidden');
                            var v = np.querySelector('video');
                            if (v) { v.pause(); v.src = ''; }
                        }
                        var cat = document.getElementById('catalog');
                        if (cat) cat.classList.remove('hidden');
                        var fil = document.getElementById('fileiras');
                        if (fil) fil.classList.remove('hidden');
                        var cg = document.getElementById('catalog-grid');
                        if (cg) cg.classList.remove('hidden');
                        document.documentElement.classList.remove('no-titulo', 'sem-conta', 'conferindo-conta');
                        document.documentElement.classList.add('pagina-livre');
                        var catKind = document.querySelector('input[name="cat-kind"]:checked') || document.querySelector('input[name="cat-kind"]');
                        if (catKind) { catKind.dispatchEvent(new Event('change', { bubbles: true })); }
                    }
                } catch(e) {}
            })();
            """.trimIndent(),
            null
        )
    }

    val isNativePlayerActive = playerViewModel.playerState.collectAsState().value.currentStream != null

    // Silence any playing web video/audio whenever native player becomes active
    LaunchedEffect(isNativePlayerActive) {
        if (isNativePlayerActive) {
            webViewInstance?.evaluateJavascript(
                """
                try {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        videos[i].pause();
                    }
                    var np = document.getElementById('nosso-player');
                    if (np) {
                        var v = np.querySelector('video');
                        if (v) v.pause();
                    }
                } catch(e) {}
                """.trimIndent(),
                null
            )
        }
    }

    val playerState by playerViewModel.playerState.collectAsState()

    // Sync playback progress back to website's bvd:progresso so "Continuar assistindo" catalog row updates
    LaunchedEffect(playerState.currentPositionMs) {
        val stream = playerState.currentStream
        val posMs = playerState.currentPositionMs
        val durMs = playerState.totalDurationMs
        if (stream != null && posMs > 5_000L) {
            val title = if (!repository.isGenericTitle(stream.title)) stream.title else (activeMovieTitle ?: "")
            if (title.isNotBlank()) {
                val sec = posMs / 1000L
                val durSec = if (durMs > 0) durMs / 1000L else stream.durationSeconds
                val safeTitle = title.replace("\\", "\\\\").replace("'", "\\'")
                webViewInstance?.evaluateJavascript(
                    "if (window.__syncProgressToWeb) { window.__syncProgressToWeb('$safeTitle', $sec, $durSec); }",
                    null
                )
            }
        }
    }

    // Keep banner clean - only populated when user explicitly requested watch
    LaunchedEffect(isUserInMovie, userRequestedWatch, detectedList) {
        if (!isUserInMovie || !userRequestedWatch) {
            latestBannerStream = null
        }
    }

    // When genuine stream (>15m) arrives after user requested watch, auto-open immediately
    LaunchedEffect(Unit) {
        repository.newMovieAlert.collectLatest { stream ->
            if (isUserInMovie && stream.isAbove15Min && stream.durationSeconds >= 900L) {
                if (userRequestedWatch) {
                    userRequestedWatch = false
                    latestBannerStream = null
                    onPlayStream(stream)
                }
            }
        }
    }

    val performBackNavigation: () -> Unit = {
        if (customFullscreenView != null) {
            customViewCallback?.onCustomViewHidden()
            customFullscreenView = null
            customViewCallback = null
        } else {
            webViewInstance?.evaluateJavascript(
                "(function() { return (window.__handleBackNavigation ? window.__handleBackNavigation() : 'none'); })();"
            ) { result ->
                val cleanResult = result?.replace("\"", "")?.trim() ?: ""
                when (cleanResult) {
                    "menu_closed", "genres_closed", "search_closed", "ep_picker_closed", "category_cleared" -> {
                        lastBackPressTime = 0L
                    }
                    "returned_to_detalhe" -> {
                        isUserInMovie = true
                        userRequestedWatch = false
                        latestBannerStream = null
                        repository.clearDetected()
                        lastBackPressTime = 0L
                    }
                    "returned_to_catalog" -> {
                        isUserInMovie = false
                        userRequestedWatch = false
                        latestBannerStream = null
                        activeMovieTitle = null
                        repository.clearDetected()
                        lastBackPressTime = 0L
                    }
                    "focused_header", "focused_menu" -> {
                        // Moveu o foco de volta para o cabeçalho (Filmes / Séries / Menu) na Home (Imagem 2)
                        lastBackPressTime = 0L
                    }
                    "exit_prompt" -> {
                        val now = System.currentTimeMillis()
                        if (now - lastBackPressTime < 2000) {
                            (context as? android.app.Activity)?.finish()
                        } else {
                            lastBackPressTime = now
                            Toast.makeText(context, "Pressione voltar novamente para sair do aplicativo", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        if (isUserInMovie) {
                            returnToCatalogInOneClick()
                        } else if (canGoBack && webViewInstance?.canGoBack() == true) {
                            webViewInstance?.goBack()
                            webViewInstance?.postDelayed({
                                webViewInstance?.evaluateJavascript(
                                    "if (window.__checkCatalogState) window.__checkCatalogState();",
                                    null
                                )
                            }, 300)
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
                }
            }
        }
    }

    // Listen to remote control and keyboard actions targeting the web player
    LaunchedEffect(webViewInstance) {
        playerViewModel.webKeyEvents.collectLatest { action ->
            when (action) {
                PlayerViewModel.WebKeyAction.HANDLE_BACK -> {
                    performBackNavigation()
                }
                PlayerViewModel.WebKeyAction.RETURN_TO_AUDIO_SELECTION -> {
                    userRequestedWatch = false
                    latestBannerStream = null
                    repository.clearDetected()
                    isUserInMovie = true
                    webViewInstance?.evaluateJavascript(
                        "if(window.__returnToMovieAudioSelection) { window.__returnToMovieAudioSelection(); }",
                        null
                    )
                }
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
                PlayerViewModel.WebKeyAction.DPAD_UP -> {
                    if (isMouseModeActive) {
                        cursorY = (cursorY - 45f).coerceAtLeast(10f)
                        dispatchHoverAt(cursorX, cursorY)
                    }
                }
                PlayerViewModel.WebKeyAction.DPAD_DOWN -> {
                    if (isMouseModeActive) {
                        val maxH = (webViewInstance?.height?.toFloat() ?: 1200f) - 10f
                        cursorY = (cursorY + 45f).coerceAtMost(maxH)
                        dispatchHoverAt(cursorX, cursorY)
                    }
                }
                PlayerViewModel.WebKeyAction.DPAD_LEFT -> {
                    if (isMouseModeActive) {
                        cursorX = (cursorX - 45f).coerceAtLeast(10f)
                        dispatchHoverAt(cursorX, cursorY)
                    }
                }
                PlayerViewModel.WebKeyAction.DPAD_RIGHT -> {
                    if (isMouseModeActive) {
                        val maxW = (webViewInstance?.width?.toFloat() ?: 1200f) - 10f
                        cursorX = (cursorX + 45f).coerceAtMost(maxW)
                        dispatchHoverAt(cursorX, cursorY)
                    }
                }
                PlayerViewModel.WebKeyAction.DPAD_CENTER -> {
                    if (isMouseModeActive) {
                        isClicking = true
                        dispatchClickAt(cursorX, cursorY)
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(160)
                            isClicking = false
                        }
                    } else {
                        webViewInstance?.evaluateJavascript(
                            "if(window.__handleTvRemoteOk) { window.__handleTvRemoteOk(); }",
                            null
                        )
                    }
                }
                PlayerViewModel.WebKeyAction.TOGGLE_MENU -> {
                    val next = playerViewModel.toggleMouseMode()
                    isMouseModeActive = next
                    mouseHudMessage = if (next) "Modo Mouse ATIVADO: Use as setas para mover e OK para clicar" else "Modo de Teclas ATIVADO"
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(3000)
                        mouseHudMessage = null
                    }
                    if (next) {
                        if (cursorX <= 10f || cursorY <= 10f) {
                            cursorX = (webViewInstance?.width?.toFloat() ?: 800f) / 2f
                            cursorY = (webViewInstance?.height?.toFloat() ?: 600f) / 2f
                        }
                        dispatchHoverAt(cursorX, cursorY)
                    }
                }
            }
        }
    }

    // Back handling: only active when native player overlay is NOT active
    // Follows strict 3-tier hierarchy: Server Frame / Cloudflare -> Movie Details (#detalhe) -> Catalog
    BackHandler(enabled = !isNativePlayerActive) {
        performBackNavigation()
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Toolbar - exists in codebase, but hidden per user request: "isso aqui pode ter mas nao pode ficar visivel imagem 1"
            var showTopToolbar by remember { mutableStateOf(false) }
            if (showTopToolbar) {
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
                                if (isUserInMovie) {
                                    returnToCatalogInOneClick()
                                } else if (webViewInstance?.canGoBack() == true) {
                                    webViewInstance?.goBack()
                                    webViewInstance?.postDelayed({
                                        webViewInstance?.evaluateJavascript(
                                            "if (window.__checkCatalogState) window.__checkCatalogState();",
                                            null
                                        )
                                    }, 300)
                                }
                            },
                            enabled = isUserInMovie || canGoBack,
                            modifier = Modifier.size(36.dp).testTag("web_btn_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = if (isUserInMovie || canGoBack) TextPrimary else TextSecondary.copy(alpha = 0.3f),
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
                                returnToCatalogInOneClick()
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

                        Button(
                            onClick = {
                                returnToCatalogInOneClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isUserInMovie) NeonAmber else CinemaSurface,
                                contentColor = if (!isUserInMovie) Color.Black else TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .padding(horizontal = 2.dp)
                                .testTag("web_btn_catalog")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = "Catálogo",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Catálogo",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
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

                            val currentWebView = this
                            android.webkit.CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(currentWebView, true)
                            }

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
                            isFocusable = true
                            isFocusableInTouchMode = true
                            keepScreenOn = true

                            // Um WebView ignora downloads: sem isto, o link do
                            // APK no site nao faz nada, nem erro aparece.
                            setDownloadListener { url, agente, _, tipo, _ ->
                                if (BaixarAtualizacao.ehApk(url, tipo)) {
                                    BaixarAtualizacao.baixar(ctx, url, agente ?: APP_USER_AGENT)
                                }
                            }

                            // JavaScript Native Bridge - Thread-safe
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onVideoFound(url: String?, title: String?, durationSec: Long) {
                                    try {
                                        if (!url.isNullOrBlank() && isUserInMovie) {
                                            val resolvedTitle = if (!title.isNullOrBlank() && !repository.isGenericTitle(title)) {
                                                title.trim()
                                            } else {
                                                activeMovieTitle ?: ""
                                            }
                                            repository.onStreamCandidate(
                                                url = url,
                                                pageTitle = resolvedTitle,
                                                pageUrl = currentUrl,
                                                initialDurationSec = durationSec,
                                                userAgent = APP_USER_AGENT
                                            )
                                        }
                                    } catch (e: Throwable) {
                                        // Ignore
                                    }
                                }

                                @JavascriptInterface
                                fun onMovieSelected(title: String?) {
                                    playerViewModel.stopPlayback()
                                    val clean = title?.trim()
                                    if (!clean.isNullOrEmpty() && !repository.isGenericTitle(clean)) {
                                        activeMovieTitle = clean
                                        repository.setCurrentMovieTitle(clean)
                                        isUserInMovie = true
                                    }
                                }

                                @JavascriptInterface
                                fun onMovieStateChanged(inMovie: Boolean, title: String?) {
                                    isUserInMovie = inMovie
                                    if (inMovie) {
                                        val clean = title?.trim()
                                        if (!clean.isNullOrEmpty() && !repository.isGenericTitle(clean)) {
                                            activeMovieTitle = clean
                                            repository.setCurrentMovieTitle(clean)
                                        }
                                    } else {
                                        onNavigatedToCatalog()
                                    }
                                }

                                fun handleAssistirClicked(title: String?, savedSec: Long) {
                                    playerViewModel.stopPlayback()
                                    val clean = title?.trim()
                                    if (!clean.isNullOrEmpty() && !repository.isGenericTitle(clean)) {
                                        activeMovieTitle = clean
                                        repository.setCurrentMovieTitle(clean)
                                        if (savedSec > 10L) {
                                            coroutineScope.launch {
                                                repository.saveProgressForTitle(clean, savedSec * 1000L)
                                            }
                                        }
                                    }
                                    isUserInMovie = true
                                    userRequestedWatch = true

                                    val valid = repository.detectedStreams.value.firstOrNull { it.isAbove15Min && it.durationSeconds >= 900L }
                                    if (valid != null) {
                                        currentWebView.post {
                                            userRequestedWatch = false
                                            latestBannerStream = null
                                            onPlayStream(valid)
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun onAssistirClicked(title: String?) {
                                    handleAssistirClicked(title, 0L)
                                }

                                @JavascriptInterface
                                fun onAssistirClicked(title: String?, savedSec: Double) {
                                    handleAssistirClicked(title, savedSec.toLong())
                                }

                                @JavascriptInterface
                                fun onAssistirClicked(title: String?, savedSec: Long) {
                                    handleAssistirClicked(title, savedSec)
                                }

                                @JavascriptInterface
                                fun onCloudProgressLoaded(title: String?, second: Long, durationSec: Long) {
                                    val clean = title?.trim()
                                    if (!clean.isNullOrEmpty() && !repository.isGenericTitle(clean) && second > 10L) {
                                        playerViewModel.updateCloudProgress(clean, second * 1000L, durationSec)
                                    }
                                }

                                @JavascriptInterface
                                fun onCloudProgressLoaded(title: String?, second: Double, durationSec: Double) {
                                    onCloudProgressLoaded(title, second.toLong(), durationSec.toLong())
                                }

                                @JavascriptInterface
                                fun onSyncLocalProgress(title: String?, second: Long, durationSec: Long) {
                                    val clean = title?.trim()
                                    if (!clean.isNullOrEmpty() && !repository.isGenericTitle(clean) && second > 10L) {
                                        coroutineScope.launch {
                                            repository.saveProgressForTitle(clean, second * 1000L, durationSec)
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun onSyncLocalProgress(title: String?, second: Double, durationSec: Double) {
                                    onSyncLocalProgress(title, second.toLong(), durationSec.toLong())
                                }

                                @JavascriptInterface
                                fun baixarAtualizacao(url: String?) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        BaixarAtualizacao.baixar(ctx, url)
                                    }
                                }

                                @JavascriptInterface
                                fun isAppUpdated(): Boolean {
                                    return BaixarAtualizacao.isAppAlreadyUpdated(ctx)
                                }

                                @JavascriptInterface
                                fun isMandatoryBlocked(): Boolean {
                                    return BaixarAtualizacao.isMandatoryBlocked(ctx)
                                }

                                @JavascriptInterface
                                fun onOpenTvSearchKeyboard(currentQuery: String?) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        tvSearchQuery = currentQuery ?: ""
                                        showTvSearchKeyboard = true
                                    }
                                }

                                @JavascriptInterface
                                fun onNavigatedToCatalog() {
                                    playerViewModel.stopPlayback()
                                    isUserInMovie = false
                                    userRequestedWatch = false
                                    activeMovieTitle = null
                                    latestBannerStream = null
                                    repository.clearDetected()
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

                                        // 1. Block external app schemes (e.g. shopee://, market://, intent://)
                                        if (!reqUrl.startsWith("http://") && !reqUrl.startsWith("https://")) {
                                            return true
                                        }

                                        // 1.1 Intercept APK downloads (GitHub release, meu-filme-novo-debug.apk or any .apk)
                                        if (BaixarAtualizacao.ehApk(reqUrl, null) ||
                                            lower.contains("meu-filme-novo-debug.apk") ||
                                            (lower.contains("github.com") && lower.contains("/releases/download/")) ||
                                            lower.endsWith(".apk")) {
                                            coroutineScope.launch(Dispatchers.Main) {
                                                BaixarAtualizacao.baixar(ctx, reqUrl)
                                            }
                                            return true
                                        }

                                        // 2. Intercept video streams internally (ignore YouTube trailer streams)
                                        val isYouTube = lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("googlevideo.com")
                                        if (!isYouTube && (lower.contains(".m3u8") || lower.contains(".mp4"))) {
                                            if (isUserInMovie) {
                                                repository.onStreamCandidate(
                                                    url = reqUrl,
                                                    pageTitle = activeMovieTitle ?: "",
                                                    pageUrl = currentUrl,
                                                    initialDurationSec = 0L,
                                                    userAgent = APP_USER_AGENT
                                                )
                                            }
                                            return true
                                        }

                                        // 3. Block external popups, stores, and ad networks
                                        val host = request.url?.host?.lowercase() ?: ""
                                        val isBlockedExternal = host.contains("shopee") ||
                                            host.contains("aliexpress") ||
                                            host.contains("mercadolivre") ||
                                            host.contains("amazon") ||
                                            host.contains("magalu") ||
                                            host.contains("bet365") ||
                                            host.contains("betano") ||
                                            host.contains("doubleclick") ||
                                            host.contains("adsterra") ||
                                            host.contains("popunder") ||
                                            host.contains("googleads") ||
                                            lower.contains("affiliate") ||
                                            lower.contains("tracking")

                                        if (isBlockedExternal) {
                                            return true
                                        }

                                        // 4. Strict whitelist: allow our app system, authorized video embeds, stream hosts, and update repositories
                                        val isAllowedSystem = host.isEmpty() ||
                                            host.contains("meufilme") ||
                                            host.contains("filmemeu") ||
                                            host.contains("github.com") ||
                                            host.contains("githubusercontent.com") ||
                                            host.contains("myembed") ||
                                            host.contains("playerflix") ||
                                            host.contains("vsembed") ||
                                            host.contains("superflix") ||
                                            host.contains("warezcdn") ||
                                            host.contains("vidmoly") ||
                                            host.contains("streamwish") ||
                                            host.contains("filemoon") ||
                                            host.contains("dood") ||
                                            host.contains("mixdrop") ||
                                            host.contains("voe.sx") ||
                                            host.contains("streamtape") ||
                                            host.contains("streamcrypt") ||
                                            host.contains("uqload") ||
                                            host.contains("embedder") ||
                                            host.contains("embedmovies") ||
                                            host.contains("vercel.app") ||
                                            host.contains("supabase") ||
                                            host.contains("themoviedb") ||
                                            host.contains("tmdb") ||
                                            host.contains("hclod.qzz.io") ||
                                            host.contains("youtube.com") ||
                                            host.contains("youtube-nocookie.com") ||
                                            host.contains("googlevideo.com") ||
                                            host.contains("ytimg.com") ||
                                            host.contains("google.com") ||
                                            host.contains("gstatic.com") ||
                                            (isUserInMovie && (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains("/manifest")))

                                        if (isAllowedSystem) {
                                            return false
                                        }

                                        // Block any outside store/ad domain so the user stays strictly in our system
                                        return true
                                    } catch (e: Exception) {
                                        return true
                                    }
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    try {
                                        val reqUrl = request?.url?.toString()
                                        if (reqUrl != null && isUserInMovie) {
                                            val lower = reqUrl.lowercase()
                                            val isYouTube = lower.contains("youtube.com") || lower.contains("googlevideo.com") || lower.contains("ytimg.com")
                                            if (!isYouTube && (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".m4s") || lower.contains("playlist.m3u8") || lower.contains("/manifest"))) {
                                                repository.onStreamCandidate(
                                                    url = reqUrl,
                                                    pageTitle = activeMovieTitle ?: "",
                                                    pageUrl = currentUrl,
                                                    initialDurationSec = 0L,
                                                    userAgent = APP_USER_AGENT
                                                )
                                            }

                                            // Subframe script injection for playerflix / myembed to auto-select language/server, skip ads, and auto-play
                                            val isEmbedServer = (lower.contains("playerflix.ink") ||
                                                                 lower.contains("myembed.biz")) &&
                                                                request.method.equals("GET", ignoreCase = true)
                                            if (isEmbedServer) {
                                                val injected = injectAutomationIntoEmbed(reqUrl, request)
                                                if (injected != null) return injected
                                            }
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
                                        if (url == DEFAULT_URL || url.contains("meufilme.vercel.app") || url.contains("filmemeu.vercel.app")) {
                                            isUserInMovie = false
                                            activeMovieTitle = null
                                            latestBannerStream = null
                                            repository.clearDetected()
                                        }
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
                                    if (!title.isNullOrBlank() && !repository.isGenericTitle(title)) {
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

                // Detected Movie Banner at the bottom of the browser - only shown when inside a movie and stream has valid duration (> 15m)
                // Commercials/ads with unknown duration are strictly excluded
                DetectedStreamBanner(
                    stream = if (isUserInMovie && latestBannerStream?.isAbove15Min == true && (latestBannerStream?.durationSeconds ?: 0L) >= 900L) latestBannerStream else null,
                    onPlay = { stream ->
                        onPlayStream(stream)
                    },
                    onDismiss = { latestBannerStream = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )

                // Virtual TV Mouse Overlay
                if (isMouseModeActive) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Floating Mode Toggle Chip Button
                        Surface(
                            onClick = {
                                val next = playerViewModel.toggleMouseMode()
                                isMouseModeActive = next
                                mouseHudMessage = if (next) "Modo Mouse ATIVADO: Use as setas para mover e OK para clicar" else "Modo de Teclas ATIVADO"
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(2500)
                                    mouseHudMessage = null
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = CinemaDark.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, NeonCyan),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = "Mouse TV",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Mouse Virtual [OK]",
                                    color = NeonCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Animated Pointer Cursor
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(cursorX.roundToInt(), cursorY.roundToInt()) }
                                .size(if (isClicking) 32.dp else 26.dp)
                        ) {
                            if (isClicking) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(NeonCyan.copy(alpha = 0.45f), shape = RoundedCornerShape(50))
                                )
                            }
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val primary = androidx.compose.ui.graphics.Color(0xFF00E5FF)
                                val darkStroke = androidx.compose.ui.graphics.Color(0xFF050912)

                                val arrowPath = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(size.width * 0.72f, size.height * 0.48f)
                                    lineTo(size.width * 0.44f, size.height * 0.52f)
                                    lineTo(size.width * 0.64f, size.height)
                                    lineTo(size.width * 0.44f, size.height)
                                    lineTo(size.width * 0.28f, size.height * 0.58f)
                                    lineTo(0f, size.height * 0.82f)
                                    close()
                                }
                                drawPath(arrowPath, color = darkStroke, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f))
                                drawPath(arrowPath, color = primary)
                            }
                        }

                        // Floating instruction HUD
                        if (mouseHudMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CinemaBlack.copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, NeonCyan),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 65.dp)
                            ) {
                                Text(
                                    text = mouseHudMessage ?: "",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
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

    // Native TV Box On-Screen Search Keyboard Dialog (controlled via D-pad & OK)
    if (showTvSearchKeyboard) {
        TvSearchDialog(
            initialQuery = tvSearchQuery,
            onQueryChange = { newQuery ->
                tvSearchQuery = newQuery
                val safeQuery = newQuery.replace("\\", "\\\\").replace("'", "\\'")
                webViewInstance?.evaluateJavascript(
                    "if (window.__updateSearchQuery) window.__updateSearchQuery('$safeQuery');",
                    null
                )
            },
            onDismiss = {
                showTvSearchKeyboard = false
                if (tvSearchQuery.isBlank()) {
                    webViewInstance?.evaluateJavascript(
                        "if (window.__closeSearch) window.__closeSearch();",
                        null
                    )
                }
            },
            onSearchConfirmed = { finalQuery ->
                showTvSearchKeyboard = false
                val safeQuery = finalQuery.replace("\\", "\\\\").replace("'", "\\'")
                webViewInstance?.evaluateJavascript(
                    "if (window.__updateSearchQuery) window.__updateSearchQuery('$safeQuery');",
                    null
                )
            }
        )
    }
}

@Composable
private fun TvSearchDialog(
    initialQuery: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSearchConfirmed: (String) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }

    val rows = remember {
        listOf(
            listOf("A", "B", "C", "D", "E", "F", "G", "1", "2", "3"),
            listOf("H", "I", "J", "K", "L", "M", "N", "4", "5", "6"),
            listOf("O", "P", "Q", "R", "S", "T", "U", "7", "8", "9"),
            listOf("V", "W", "X", "Y", "Z", "-", " ", ".", "0", "⌫")
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CinemaBlack.copy(alpha = 0.92f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .background(CinemaDark, shape = RoundedCornerShape(16.dp))
                    .border(1.5.dp, CinemaCardBorder, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Buscar no Catálogo (Controle Remoto)",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search query display bar
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CinemaBlack,
                    border = BorderStroke(1.5.dp, if (query.isNotEmpty()) NeonCyan else CinemaCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (query.isNotEmpty()) query else "Digite o nome usando as setas e OK...",
                            color = if (query.isNotEmpty()) TextPrimary else TextSecondary,
                            fontSize = 16.sp,
                            fontWeight = if (query.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (query.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    query = ""
                                    onQueryChange("")
                                }
                            ) {
                                Text("Limpar", color = NeonAmber, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TV Virtual Keyboard Rows (D-pad navigable)
                rows.forEach { rowKeys ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        rowKeys.forEach { keyLabel ->
                            val isBackspace = keyLabel == "⌫"
                            val isSpace = keyLabel == " "

                            Button(
                                onClick = {
                                    if (isBackspace) {
                                        if (query.isNotEmpty()) {
                                            query = query.dropLast(1)
                                            onQueryChange(query)
                                        }
                                    } else {
                                        query += keyLabel
                                        onQueryChange(query)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBackspace) CinemaCardBorder else CinemaSurface,
                                    contentColor = if (isBackspace) NeonAmber else TextPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(44.dp)
                                    .then(
                                        if (isSpace) Modifier.width(68.dp)
                                        else if (isBackspace) Modifier.width(52.dp)
                                        else Modifier.width(44.dp)
                                    )
                            ) {
                                Text(
                                    text = if (isSpace) "Espaço" else keyLabel,
                                    fontSize = if (isSpace) 11.sp else 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Fechar", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            onSearchConfirmed(query)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = CinemaBlack
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("✓ Concluir Busca", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun injectAutomationIntoEmbed(urlStr: String, request: WebResourceRequest): WebResourceResponse? {
    return try {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", APP_USER_AGENT)
            request.requestHeaders.forEach { (k, v) ->
                if (!k.equals("Host", ignoreCase = true)) {
                    setRequestProperty(k, v)
                }
            }
            if (urlStr.contains("playerflix.ink")) {
                setRequestProperty("Referer", "https://myembed.biz/")
            } else if (urlStr.contains("myembed.biz")) {
                setRequestProperty("Referer", "https://filmemeu.vercel.app/")
            }
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return null
        }

        val rawContentType = conn.contentType ?: "text/html; charset=UTF-8"
        val mimeType = rawContentType.split(";").firstOrNull()?.trim() ?: "text/html"
        val charsetName = if (rawContentType.contains("charset=", ignoreCase = true)) {
            rawContentType.substringAfter("charset=").substringBefore(";").trim()
        } else {
            "UTF-8"
        }
        val charset = try { Charset.forName(charsetName) } catch (e: Exception) { Charsets.UTF_8 }

        val originalHtml = conn.inputStream.bufferedReader(charset).use { it.readText() }

        val scriptToInject = """
        <script>
        (function() {
            try {
                var style = document.createElement('style');
                style.textContent = [
                    '.icon-btn, .panel-item, button, [role="button"], .option, .skip, .play { cursor: pointer !important; }',
                    '.icon-btn:hover, .icon-btn:focus, .panel-item:hover, .panel-item:focus, button:focus, .option:focus { outline: 3px solid #00E5FF !important; box-shadow: 0 0 16px rgba(0, 229, 255, 0.8) !important; transform: scale(1.08) !important; transition: transform 0.15s ease !important; }'
                ].join('\n');
                document.head.appendChild(style);
            } catch(e) {}

            var clickedOption = false;
            function autoSelectLanguageAndServer() {
                if (clickedOption) return;
                try {
                    var items = document.querySelectorAll('.panel-item, #optionList .option, .option, [onclick*="player"]');
                    if (items && items.length > 0) {
                        var chosen = null;
                        for (var i = 0; i < items.length; i++) {
                            var item = items[i];
                            if (item && item.offsetParent !== null) {
                                var txt = (item.textContent || '').toLowerCase();
                                if (txt.indexOf('dub') !== -1 || txt.indexOf('dublado') !== -1) {
                                    chosen = item;
                                    break;
                                }
                                if (!chosen) chosen = item;
                            }
                        }
                        if (chosen) {
                            chosen.click();
                            clickedOption = true;
                        }
                    }
                } catch(e) {}
            }
            setInterval(autoSelectLanguageAndServer, 300);

            function autoSkipAds() {
                try {
                    var skipBtns = document.querySelectorAll(
                        '[class*="skip" i], [id*="skip" i], .videoAdUiSkipButton, .vast-skip-button, .ytp-ad-skip-button, button.skip, .skip-btn'
                    );
                    for (var i = 0; i < skipBtns.length; i++) {
                        var sb = skipBtns[i];
                        if (sb && sb.offsetParent !== null) {
                            sb.click();
                        }
                    }
                } catch(e) {}
            }
            setInterval(autoSkipAds, 400);

            var playedOnce = false;
            function safeAutoPlay() {
                if (playedOnce) return;
                try {
                    var playBtn = document.querySelector('.play-btn, .vjs-big-play-button, .jw-display-icon-container, [aria-label*="Play" i], button.play');
                    if (playBtn && playBtn.offsetParent !== null) {
                        playBtn.click();
                        playedOnce = true;
                        return;
                    }
                    var vids = document.querySelectorAll('video');
                    for (var j = 0; j < vids.length; j++) {
                        var v = vids[j];
                        if (v && v.paused && !v.__triedAutoPlay) {
                            v.__triedAutoPlay = true;
                            v.muted = false;
                            v.play().then(function() { playedOnce = true; }).catch(function() {
                                v.muted = true;
                                v.play().catch(function(){});
                            });
                            break;
                        }
                    }
                } catch(e) {}
            }
            setInterval(safeAutoPlay, 500);
        })();
        </script>
        """.trimIndent()

        val modifiedHtml = if (originalHtml.contains("</body>", ignoreCase = true)) {
            originalHtml.replace("</body>", "$scriptToInject\n</body>", ignoreCase = true)
        } else {
            "$originalHtml\n$scriptToInject"
        }

        val bytes = modifiedHtml.toByteArray(Charsets.UTF_8)
        val headers = mutableMapOf<String, String>()
        headers["Access-Control-Allow-Origin"] = "*"
        WebResourceResponse(mimeType, "UTF-8", 200, "OK", headers, ByteArrayInputStream(bytes))
    } catch (e: Exception) {
        null
    }
}

