package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.CinemaDark
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.update.BaixarAtualizacao
import java.util.Locale

@Composable
fun UpdateProgressDialog(
    state: BaixarAtualizacao.DownloadState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit
) {
    val primaryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state) {
        try {
            primaryFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = {
            if (state !is BaixarAtualizacao.DownloadState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is BaixarAtualizacao.DownloadState.Downloading,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 460.dp)
                .padding(24.dp)
                .testTag("update_progress_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = when (state) {
                                is BaixarAtualizacao.DownloadState.Error -> NeonAmber.copy(alpha = 0.15f)
                                is BaixarAtualizacao.DownloadState.ReadyToInstall -> Color(0xFF00C853).copy(alpha = 0.15f)
                                else -> NeonCyan.copy(alpha = 0.15f)
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = when (state) {
                                is BaixarAtualizacao.DownloadState.Error -> NeonAmber.copy(alpha = 0.4f)
                                is BaixarAtualizacao.DownloadState.ReadyToInstall -> Color(0xFF00C853).copy(alpha = 0.4f)
                                else -> NeonCyan.copy(alpha = 0.4f)
                            },
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (state) {
                            is BaixarAtualizacao.DownloadState.Error -> Icons.Default.Error
                            is BaixarAtualizacao.DownloadState.ReadyToInstall -> Icons.Default.CheckCircle
                            else -> Icons.Default.SystemUpdate
                        },
                        contentDescription = "Atualização",
                        tint = when (state) {
                            is BaixarAtualizacao.DownloadState.Error -> NeonAmber
                            is BaixarAtualizacao.DownloadState.ReadyToInstall -> Color(0xFF00C853)
                            else -> NeonCyan
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Atualização MARIOFLIX",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (state) {
                    is BaixarAtualizacao.DownloadState.Downloading -> {
                        Text(
                            text = "Baixando nova versão do aplicativo...",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (state.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = NeonCyan,
                                trackColor = CinemaDark
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = NeonCyan,
                                trackColor = CinemaDark
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.progressPercent}%",
                                color = NeonCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.totalBytes > 0) {
                                Text(
                                    text = "${formatFileSize(state.bytesDownloaded)} / ${formatFileSize(state.totalBytes)}",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            } else {
                                Text(
                                    text = formatFileSize(state.bytesDownloaded),
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .focusRequester(primaryFocusRequester)
                                .testTag("cancel_download_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar", color = TextSecondary)
                        }
                    }

                    is BaixarAtualizacao.DownloadState.ReadyToInstall -> {
                        Text(
                            text = "Download concluído com sucesso!",
                            color = Color(0xFF00C853),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "O instalador do Android foi aberto. Se necessário, clique no botão abaixo para abrir a instalação manualmente.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dismiss_update_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Fechar", color = TextSecondary)
                            }

                            Button(
                                onClick = onInstall,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .focusRequester(primaryFocusRequester)
                                    .testTag("install_update_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF064DEB),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Instalar Agora", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is BaixarAtualizacao.DownloadState.Error -> {
                        Text(
                            text = state.message,
                            color = NeonAmber,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("close_update_error_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Fechar", color = TextSecondary)
                            }

                            Button(
                                onClick = onRetry,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .focusRequester(primaryFocusRequester)
                                    .testTag("retry_update_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Tentar Novamente", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    BaixarAtualizacao.DownloadState.Idle -> {
                        // Dialog is not shown when Idle
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
