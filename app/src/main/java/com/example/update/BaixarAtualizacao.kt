package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Gerenciador nativo de atualizações do MARIOFLIX.
 *
 * Funcionalidades:
 * 1. Baixa diretamente o APK com OkHttp em segundo plano (seguindo redirects do GitHub)
 * 2. Exibe progresso em tempo real e abre o instalador do pacote nativo
 * 3. Regra mandatória de até duas atualizações:
 *    - Se o app estiver 2 ou mais versões desatualizado (remoteCode - currentCode >= 2),
 *      bloqueia completamente o acesso ("não deixa acessar nada") tanto na TV Box quanto no Smartphone.
 *    - Se o app estiver até 1 atualização atrás, permite o uso normal.
 * 4. Foco D-Pad automático no botão de atualizar para navegabilidade imediata no controle da TV.
 */
object BaixarAtualizacao {

    const val APK_DEFAULT_URL =
        "https://github.com/marao21/meu-filme-novo/releases/download/apk-latest/meu-filme-novo-debug.apk"
    private const val RAW_GRADLE_URL =
        "https://raw.githubusercontent.com/marao21/meu-filme-novo/main/app/build.gradle.kts"
    private const val GITHUB_RELEASE_API_URL =
        "https://api.github.com/repos/marao21/meu-filme-novo/releases/tags/apk-latest"

    private const val NOME_ARQUIVO = "meu-filme-novo-debug.apk"
    private const val PREFS_NAME = "marioflix_updates"
    private const val KEY_INSTALLED_V20 = "update_installed_v20"
    private const val KEY_LAST_DOWNLOADED = "last_downloaded_timestamp"
    private const val KEY_CACHED_REMOTE_VERSION_CODE = "cached_remote_version_code"
    private const val KEY_CACHED_REMOTE_VERSION_NAME = "cached_remote_version_name"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"

    private val scope = CoroutineScope(Dispatchers.Main)
    private var downloadJob: Job? = null
    private var checkVersionJob: Job? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(
            val bytesDownloaded: Long,
            val totalBytes: Long,
            val progressPercent: Int
        ) : DownloadState()
        object ReadyToInstall : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    data class VersionInfo(
        val currentVersionCode: Int = BuildConfig.VERSION_CODE,
        val currentVersionName: String = BuildConfig.VERSION_NAME,
        val remoteVersionCode: Int = BuildConfig.VERSION_CODE,
        val remoteVersionName: String = BuildConfig.VERSION_NAME,
        val isMandatoryBlock: Boolean = false,
        val isUpdateAvailable: Boolean = false,
        val updatesBehind: Int = 0
    )

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _versionInfo = MutableStateFlow(VersionInfo())
    val versionInfo: StateFlow<VersionInfo> = _versionInfo.asStateFlow()

    fun ehApk(url: String, mimeType: String?): Boolean {
        val lower = url.lowercase()
        val semParametros = lower.substringBefore('?').substringBefore('#')
        if (semParametros.endsWith(".apk") || lower.contains(".apk")) return true
        if (lower.contains("/releases/download/") && lower.contains("meu-filme-novo")) return true
        return mimeType?.contains("vnd.android.package-archive", ignoreCase = true) == true
    }

    /**
     * Retorna se o app já está atualizado.
     * Se o versionCode for >= 20 (ou se a atualização já foi instalada/baixada),
     * a faixa de atualização não deve mais ficar aparecendo.
     */
    fun isAppAlreadyUpdated(context: Context): Boolean {
        val currentCode = BuildConfig.VERSION_CODE
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedRemoteCode = prefs.getInt(KEY_CACHED_REMOTE_VERSION_CODE, currentCode)
        if (currentCode >= cachedRemoteCode) return true
        return prefs.getBoolean(KEY_INSTALLED_V20, false)
    }

    fun isMandatoryBlocked(context: Context): Boolean {
        if (_versionInfo.value.isMandatoryBlock) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedCode = prefs.getInt(KEY_CACHED_REMOTE_VERSION_CODE, BuildConfig.VERSION_CODE)
        return (cachedCode - BuildConfig.VERSION_CODE) >= 2
    }

    fun markUpdateAsDone(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_INSTALLED_V20, true)
            .putLong(KEY_LAST_DOWNLOADED, System.currentTimeMillis())
            .apply()
    }

    /**
     * Verifica a versão remota disponível no repositório.
     * Se a diferença for de 2 ou mais atualizações (diff >= 2), ativa o bloqueio mandatório.
     */
    fun verificarVersao(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedCode = prefs.getInt(KEY_CACHED_REMOTE_VERSION_CODE, BuildConfig.VERSION_CODE)
        val cachedName = prefs.getString(KEY_CACHED_REMOTE_VERSION_NAME, BuildConfig.VERSION_NAME) ?: BuildConfig.VERSION_NAME
        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME

        // Avaliação instantânea a partir do cache local
        val cachedDiff = cachedCode - currentCode
        val cachedBlock = cachedDiff >= 2
        val cachedAvail = cachedDiff >= 1
        _versionInfo.value = VersionInfo(
            currentVersionCode = currentCode,
            currentVersionName = currentName,
            remoteVersionCode = cachedCode,
            remoteVersionName = cachedName,
            isMandatoryBlock = cachedBlock,
            isUpdateAvailable = cachedAvail,
            updatesBehind = cachedDiff.coerceAtLeast(0)
        )

        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 10 * 60 * 1000L && cachedCode > 0) {
            return
        }

        checkVersionJob?.cancel()
        checkVersionJob = scope.launch(Dispatchers.IO) {
            var remoteCode: Int? = null
            var remoteName: String? = null

            // 1. Tenta carregar diretamente o build.gradle.kts do repositório no GitHub (sem limites de API)
            try {
                val req = Request.Builder()
                    .url(RAW_GRADLE_URL)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .header("Accept", "text/plain")
                    .build()

                val res = httpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val bodyStr = res.body?.string() ?: ""
                    val matchCode = Regex("""versionCode\s*=\s*(\d+)""").find(bodyStr)
                    val matchName = Regex("""versionName\s*=\s*["']([^"']+)["']""").find(bodyStr)
                    remoteCode = matchCode?.groupValues?.get(1)?.toIntOrNull()
                    remoteName = matchName?.groupValues?.get(1)
                }
            } catch (_: Exception) {}

            // 2. Fallback: API de release do GitHub
            if (remoteCode == null) {
                try {
                    val req = Request.Builder()
                        .url(GITHUB_RELEASE_API_URL)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                        .header("Accept", "application/vnd.github+json")
                        .build()

                    val res = httpClient.newCall(req).execute()
                    if (res.isSuccessful) {
                        val bodyStr = res.body?.string() ?: ""
                        val matchVer = Regex("""versionCode[:=]\s*(\d+)""").find(bodyStr)
                        remoteCode = matchVer?.groupValues?.get(1)?.toIntOrNull()
                    }
                } catch (_: Exception) {}
            }

            val resolvedCode = remoteCode ?: cachedCode
            val resolvedName = remoteName ?: cachedName

            prefs.edit()
                .putInt(KEY_CACHED_REMOTE_VERSION_CODE, resolvedCode)
                .putString(KEY_CACHED_REMOTE_VERSION_NAME, resolvedName)
                .putLong(KEY_LAST_CHECK_TIME, now)
                .apply()

            val diff = resolvedCode - currentCode
            val isBlock = diff >= 2
            val isAvail = diff >= 1

            withContext(Dispatchers.Main) {
                _versionInfo.value = VersionInfo(
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    remoteVersionCode = resolvedCode,
                    remoteVersionName = resolvedName,
                    isMandatoryBlock = isBlock,
                    isUpdateAvailable = isAvail,
                    updatesBehind = diff.coerceAtLeast(0)
                )
            }
        }
    }

    /**
     * Permite simular versões remotas para testes de conformidade
     */
    fun setSimulatedRemoteVersion(context: Context, simulatedCode: Int, simulatedName: String) {
        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME
        val diff = simulatedCode - currentCode
        val isBlock = diff >= 2
        val isAvail = diff >= 1

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_CACHED_REMOTE_VERSION_CODE, simulatedCode)
            .putString(KEY_CACHED_REMOTE_VERSION_NAME, simulatedName)
            .apply()

        _versionInfo.value = VersionInfo(
            currentVersionCode = currentCode,
            currentVersionName = currentName,
            remoteVersionCode = simulatedCode,
            remoteVersionName = simulatedName,
            isMandatoryBlock = isBlock,
            isUpdateAvailable = isAvail,
            updatesBehind = diff.coerceAtLeast(0)
        )
    }

    fun baixar(context: Context, url: String? = null, userAgent: String? = null) {
        if (_downloadState.value is DownloadState.Downloading) {
            Toast.makeText(context, "Download da atualização já em andamento...", Toast.LENGTH_SHORT).show()
            return
        }

        val targetUrl = if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
            url
        } else {
            APK_DEFAULT_URL
        }

        val appContext = context.applicationContext

        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _downloadState.value = DownloadState.Downloading(0L, 0L, 0)
                    Toast.makeText(appContext, "Iniciando download da atualização...", Toast.LENGTH_SHORT).show()
                }

                val downloadDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: appContext.cacheDir
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val targetFile = File(downloadDir, NOME_ARQUIVO)
                val tempFile = File(downloadDir, "$NOME_ARQUIVO.tmp")
                if (tempFile.exists()) tempFile.delete()

                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", userAgent ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        _downloadState.value = DownloadState.Error("Servidor retornou erro HTTP ${response.code}")
                    }
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    withContext(Dispatchers.Main) {
                        _downloadState.value = DownloadState.Error("Arquivo vazio retornado pelo servidor")
                    }
                    return@launch
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var lastReportedPercent = -1

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val percent = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }

                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        _downloadState.value = DownloadState.Downloading(
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalBytes,
                            progressPercent = percent
                        )
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                if (tempFile.length() < 100 * 1024) {
                    tempFile.delete()
                    withContext(Dispatchers.Main) {
                        _downloadState.value = DownloadState.Error("Download incompleto ou corrompido.")
                    }
                    return@launch
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                markUpdateAsDone(appContext)

                withContext(Dispatchers.Main) {
                    _downloadState.value = DownloadState.ReadyToInstall
                    abrirInstalador(appContext, targetFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _downloadState.value = DownloadState.Error("Falha no download: ${e.localizedMessage ?: "Erro de conexão"}")
                }
            }
        }
    }

    fun abrirInstalador(context: Context, file: File? = null) {
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        val apkFile = file ?: File(downloadDir, NOME_ARQUIVO)

        if (!apkFile.exists() || apkFile.length() < 100 * 1024) {
            _downloadState.value = DownloadState.Error("Arquivo APK não encontrado.")
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            // No Android 8.0+, verifica permissão de instalar apps desconhecidos
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    Toast.makeText(
                        context,
                        "Permita instalar apps desta fonte e clique em Instalar.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error("Não foi possível iniciar o instalador: ${e.message}")
        }
    }

    fun cancelar() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
    }

    fun dismiss() {
        _downloadState.value = DownloadState.Idle
    }
}
