package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.player.PlayerViewModel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.CinemaDark
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.SystemBg
import com.example.ui.theme.SystemBgSoft
import com.example.ui.theme.SystemPanel
import com.example.ui.theme.SystemLine
import com.example.ui.theme.SystemLineSoft
import com.example.ui.theme.SystemText
import com.example.ui.theme.SystemMuted
import com.example.ui.theme.SystemHeaderTab
import com.example.ui.theme.SystemAccent
import com.example.ui.theme.SystemAccentStrong
import com.example.ui.theme.SystemHls
import com.example.ui.theme.SystemMp4
import com.example.ui.theme.SystemWarn
import com.example.ui.theme.SystemDanger
import com.example.ui.theme.SystemShadowHeader
import com.example.ui.theme.SystemShadowGradient
import com.example.ui.theme.SystemPillBg
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun TVPlayerFocusButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = Color.Transparent,
    contentColor: Color = SystemText,
    focusBorderColor: Color = SystemAccent,
    focusRequester: FocusRequester? = null,
    testTag: String = "",
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = if (isFocused) BorderStroke(2.5.dp, focusBorderColor) else null,
        modifier = modifier
            .testTag(testTag)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_SPACE,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}


@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBrowse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onNavigateBack() }
    val state by viewModel.playerState.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }
    val resetControlsTimer: () -> Unit = {
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
    }

    // Auto-hide controls after 8 seconds of inactivity when playing (kept longer per user request)
    LaunchedEffect(showControls, state.isPlaying, lastInteractionTime, showQualityDialog) {
        if (showControls && state.isPlaying && !state.isLocked && !showQualityDialog) {
            delay(8000)
            showControls = false
        }
    }

    if (state.currentStream == null) {
        EmptyPlayerState(
            onNavigateToBrowse = onNavigateToBrowse,
            modifier = modifier
        )
        return
    }

    val stream = state.currentStream!!

    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    val controlsTrigger by viewModel.playerControlsTrigger.collectAsState()
    LaunchedEffect(controlsTrigger) {
        if (controlsTrigger > 0L) {
            resetControlsTimer()
        }
    }

    LaunchedEffect(Unit) {
        try {
            rootFocusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    LaunchedEffect(showControls) {
        try {
            if (showControls) {
                playPauseFocusRequester.requestFocus()
            } else {
                rootFocusRequester.requestFocus()
            }
        } catch (e: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val code = keyEvent.nativeKeyEvent.keyCode

                    // ANY key press on keyboard or remote shows controls and keeps them visible longer
                    resetControlsTimer()

                    when (code) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_SPACE,
                        KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            viewModel.togglePlayPause()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            viewModel.play()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            viewModel.pause()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                            viewModel.seekBackward10s()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                            viewModel.seekForward10s()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            true
                        }
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE -> {
                            onNavigateBack()
                            true
                        }
                        else -> {
                            // Any other key simply shows controls and keeps them visible
                            false
                        }
                    }
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showControls) {
                    showControls = false
                } else {
                    resetControlsTimer()
                }
            }
            .testTag("player_screen_root")
    ) {
        // ExoPlayer Video View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = viewModel.exoPlayer
                    useController = false
                    isFocusable = true
                    isFocusableInTouchMode = true
                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            viewModel.triggerShowControls()
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER,
                                KeyEvent.KEYCODE_SPACE,
                                KeyEvent.KEYCODE_BUTTON_A,
                                KeyEvent.KEYCODE_BUTTON_SELECT,
                                KeyEvent.KEYCODE_BUTTON_START,
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    viewModel.togglePlayPause()
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    viewModel.play()
                                    true
                                }
                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    viewModel.pause()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_MEDIA_REWIND,
                                KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    viewModel.seekBackward10s()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    viewModel.seekForward10s()
                                    true
                                }
                                KeyEvent.KEYCODE_BACK,
                                KeyEvent.KEYCODE_ESCAPE,
                                KeyEvent.KEYCODE_BUTTON_B -> {
                                    onNavigateBack()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_MENU,
                                KeyEvent.KEYCODE_INFO -> {
                                    true
                                }
                                else -> false
                            }
                        } else true
                    }
                    requestFocus()
                    keepScreenOn = true
                    resizeMode = when (state.resizeMode) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            },
            update = { playerView ->
                if (playerView.player != viewModel.exoPlayer) {
                    playerView.player = viewModel.exoPlayer
                }
                val targetResize = when (state.resizeMode) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                if (playerView.resizeMode != targetResize) {
                    playerView.resizeMode = targetResize
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Automatic Quality Adaptation Notification Banner (YouTube style)
        AnimatedVisibility(
            visible = state.autoQualityNotice != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            Surface(
                color = CinemaDark.copy(alpha = 0.94f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.8f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.autoQualityNotice ?: "",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Loading spinner
        if (state.isLoading) {
            CircularProgressIndicator(
                color = NeonCyan,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            )
        }

        // Error message banner
        if (state.errorMessage != null) {
            Surface(
                color = CinemaDark.copy(alpha = 0.95f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Erro",
                        tint = NeonRed,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = state.errorMessage ?: "Erro ao reproduzir",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.retryPlayback() },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                    ) {
                        Text(text = "Tentar Novamente", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Lock button (always visible in corner if locked)
        if (state.isLocked) {
            TVPlayerFocusButton(
                onClick = { viewModel.toggleLock() },
                containerColor = CinemaDark.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(44.dp),
                testTag = "btn_unlock_player"
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Desbloquear",
                    tint = NeonAmber,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls && !state.isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Clean top area - touch-back target for touch devices without UI clutter
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onNavigateBack() }
                )

                // Center Play/Pause Button
                Box(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    TVPlayerFocusButton(
                        onClick = {
                            viewModel.togglePlayPause()
                            resetControlsTimer()
                        },
                        shape = CircleShape,
                        containerColor = SystemAccentStrong,
                        contentColor = Color.Black,
                        focusBorderColor = SystemAccent,
                        focusRequester = playPauseFocusRequester,
                        modifier = Modifier.size(76.dp),
                        testTag = "player_btn_play_pause"
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar" else "Reproduzir",
                            tint = Color.Black,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                // Bottom Section: Clean Cinema Netflix TV style matching user screenshot
                // Prominent Movie Title & Sleek Timeline
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x99050912),
                                    Color(0xDD050912),
                                    SystemShadowGradient
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stream.title.ifEmpty { "Filme" },
                            color = SystemText,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botão de Seleção de Qualidade (estilo YouTube: AUTO / 1080p / 720p / 480p / 360p)
                            TVPlayerFocusButton(
                                onClick = {
                                    showQualityDialog = true
                                    resetControlsTimer()
                                },
                                shape = RoundedCornerShape(10.dp),
                                containerColor = SystemBgSoft.copy(alpha = 0.85f),
                                contentColor = SystemText,
                                focusBorderColor = SystemAccent,
                                modifier = Modifier.height(38.dp),
                                testTag = "player_btn_quality"
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Qualidade de Vídeo",
                                        tint = SystemAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = state.currentQualityLabel,
                                        color = SystemText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Botão de Alternar Formato de Tela (FIT, FILL, ZOOM)
                            TVPlayerFocusButton(
                                onClick = {
                                    viewModel.cycleResizeMode()
                                    resetControlsTimer()
                                },
                                shape = RoundedCornerShape(10.dp),
                                containerColor = SystemBgSoft.copy(alpha = 0.85f),
                                contentColor = SystemText,
                                focusBorderColor = SystemAccent,
                                modifier = Modifier.height(38.dp),
                                testTag = "player_btn_aspect_ratio"
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = "Formato de Tela",
                                        tint = SystemAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (state.resizeMode) {
                                            1 -> "FILL"
                                            2 -> "ZOOM"
                                            else -> "FIT"
                                        },
                                        color = SystemMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sleek Modern Timeline
                    var isSliderFocused by remember { mutableStateOf(false) }
                    Slider(
                        value = if (isSeeking) seekProgress else state.progressFraction,
                        onValueChange = { frac ->
                            isSeeking = true
                            seekProgress = frac
                        },
                        onValueChangeFinished = {
                            isSeeking = false
                            if (state.totalDurationMs > 0) {
                                val target = (seekProgress * state.totalDurationMs).toLong()
                                viewModel.seekTo(target)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = SystemAccent,
                            activeTrackColor = SystemAccent,
                            inactiveTrackColor = SystemLineSoft
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_timeline_slider")
                            .border(
                                border = if (isSliderFocused) BorderStroke(2.dp, SystemAccent) else BorderStroke(0.dp, Color.Transparent),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .onFocusChanged { isSliderFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            viewModel.seekBackward10s()
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            viewModel.seekForward10s()
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentMs = if (isSeeking) (seekProgress * state.totalDurationMs).toLong() else state.currentPositionMs
                        Text(
                            text = formatTimeMs(currentMs),
                            color = SystemText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatTimeMs(state.totalDurationMs),
                            color = SystemMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (showQualityDialog) {
            QualitySelectionDialog(
                currentPreference = state.selectedQualityPreference,
                currentLabel = state.currentQualityLabel,
                availableQualities = state.availableQualities,
                onSelectQuality = { quality: String ->
                    viewModel.setQualityPreference(quality)
                },
                onDismiss = {
                    showQualityDialog = false
                    resetControlsTimer()
                }
            )
        }
    }
}

@Composable
fun QualitySelectionDialog(
    currentPreference: String,
    currentLabel: String,
    availableQualities: List<String>,
    onSelectQuality: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = remember(availableQualities) {
        val list = mutableListOf("AUTO")
        val tiers = listOf("1080p", "720p", "480p", "360p", "320p", "240p")
        for (q in availableQualities) {
            val clean = q.substringBefore(" (").trim()
            if (clean.isNotEmpty() && !list.contains(clean)) {
                list.add(clean)
            }
        }
        for (tier in tiers) {
            if (!list.contains(tier)) {
                list.add(tier)
            }
        }
        list
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CinemaSurface,
            border = BorderStroke(1.dp, SystemLineSoft),
            modifier = Modifier
                .widthIn(min = 300.dp, max = 420.dp)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = SystemAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Qualidade do Vídeo",
                        color = SystemText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "A qualidade automática (modo YouTube) reduz a resolução se a internet estiver fraca e melhora quando a conexão restabelecer.",
                    color = SystemMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = (option == currentPreference) ||
                            (option == "AUTO" && (currentPreference == "AUTO" || currentPreference.startsWith("AUTO")))
                        val description = when (option) {
                            "AUTO" -> "Automática (Recomendada · $currentLabel)"
                            "1080p" -> "1080p (Full HD)"
                            "720p" -> "720p (HD)"
                            "480p" -> "480p (SD · Conexão Moderada)"
                            "360p" -> "360p (Econômico · Internet Lenta)"
                            "320p" -> "320p (Ultra Baixo · Internet Fraca)"
                            else -> option
                        }

                        TVPlayerFocusButton(
                            onClick = {
                                onSelectQuality(option)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            containerColor = if (isSelected) SystemAccent.copy(alpha = 0.15f) else SystemBgSoft,
                            focusBorderColor = SystemAccent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = description,
                                    color = if (isSelected) SystemAccent else SystemText,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionado",
                                        tint = SystemAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlayerState(
    onNavigateToBrowse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SystemBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = SystemPanel,
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = SystemAccent,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Nenhum vídeo em reprodução",
                color = SystemText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Navegue pelo FilmeMeu para encontrar um filme ou selecione um stream detectado.",
                color = SystemMuted,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNavigateToBrowse,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SystemAccentStrong,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_empty_go_browse")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Ir para o FilmeMeu", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}


