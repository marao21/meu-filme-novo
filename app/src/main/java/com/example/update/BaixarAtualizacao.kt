package com.example.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Baixa o APK da atualização e abre o instalador no Android e TV Box.
 *
 * Utiliza download direto com suporte a redirecionamentos (como GitHub Releases)
 * com fallback para DownloadManager e navegador externo, garantindo que funcione
 * perfeitamente tanto no smartphone quanto na TV Box.
 */
object BaixarAtualizacao {

    const val URL_APK_PADRAO = "https://github.com/marao21/meu-filme-novo/releases/download/apk-latest/meu-filme-novo-debug.apk"
    private const val NOME = "marioflix-atualizacao.apk"
    private var isDownloading = false

    fun ehApk(url: String, mimeType: String?): Boolean {
        val semParametros = url.substringBefore('?').substringBefore('#').lowercase()
        if (semParametros.endsWith(".apk")) return true
        if (semParametros.contains(".apk/")) return true
        if (semParametros.contains("/releases/download/")) return true
        if (semParametros.contains("package-archive")) return true
        return mimeType?.contains("vnd.android.package-archive", ignoreCase = true) == true
    }

    fun baixar(context: Context, url: String?, userAgent: String?) {
        val urlFinal = if (url.isNullOrBlank() || url.contains("undefined") || url.contains("null") || url == "#") {
            URL_APK_PADRAO
        } else {
            url.trim()
        }

        if (isDownloading) {
            Toast.makeText(context, "Download da atualização já está em andamento...", Toast.LENGTH_SHORT).show()
            return
        }

        val appContext = context.applicationContext
        isDownloading = true
        Toast.makeText(appContext, "Iniciando download da atualização...", Toast.LENGTH_SHORT).show()

        // 1. Download direto via Coroutines (máxima confiabilidade em TV Box e Android)
        CoroutineScope(Dispatchers.IO).launch {
            val sucesso = baixarArquivoDireto(appContext, urlFinal, userAgent)
            isDownloading = false
            if (sucesso) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Download concluído! Abrindo instalador...", Toast.LENGTH_LONG).show()
                    abrirInstalador(appContext)
                }
            } else {
                withContext(Dispatchers.Main) {
                    // Fallback para DownloadManager caso o download direto falhe
                    baixarComDownloadManager(appContext, urlFinal, userAgent)
                }
            }
        }
    }

    private fun baixarArquivoDireto(context: Context, urlStr: String, userAgent: String?): Boolean {
        val pasta = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val destino = File(pasta, NOME)
        val temporario = File(pasta, "$NOME.tmp")

        try {
            if (destino.exists()) destino.delete()
            if (temporario.exists()) temporario.delete()

            var conexaoAtualUrl = urlStr
            var redirecionamentos = 0
            var conn: HttpURLConnection? = null

            // Segue até 8 redirecionamentos (ex: GitHub Releases -> S3/Azure release-assets)
            while (redirecionamentos < 8) {
                val url = URL(conexaoAtualUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", userAgent ?: "Mozilla/5.0")
                    setRequestProperty("Accept", "*/*")
                }

                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val novoLocal = conn.getHeaderField("Location") ?: break
                    conexaoAtualUrl = if (novoLocal.startsWith("http://") || novoLocal.startsWith("https://")) {
                        novoLocal
                    } else {
                        URL(url, novoLocal).toString()
                    }
                    conn.disconnect()
                    redirecionamentos++
                } else {
                    break
                }
            }

            if (conn == null || conn.responseCode !in 200..299) {
                conn?.disconnect()
                return false
            }

            val inputStream = conn.inputStream
            val outputStream = FileOutputStream(temporario)

            val buffer = ByteArray(64 * 1024)
            var bytesLidos: Int
            while (inputStream.read(buffer).also { bytesLidos = it } != -1) {
                outputStream.write(buffer, 0, bytesLidos)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            conn.disconnect()

            if (temporario.exists() && temporario.length() > 1024L) {
                temporario.renameTo(destino)
                return destino.exists() && destino.length() > 1024L
            }
            return false
        } catch (e: Throwable) {
            temporario.delete()
            destino.delete()
            return false
        }
    }

    private fun baixarComDownloadManager(context: Context, url: String, userAgent: String?) {
        val gerenciador = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (gerenciador == null) {
            abrirNoNavegador(context, url)
            return
        }

        val pasta = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        File(pasta, NOME).takeIf { it.exists() }?.delete()

        val pedido = try {
            DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("Atualização MARIOFLIX")
                setDescription("Baixando nova versão do aplicativo")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, NOME)
                if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
            }
        } catch (e: Throwable) {
            abrirNoNavegador(context, url)
            return
        }

        val id = try {
            gerenciador.enqueue(pedido)
        } catch (e: Throwable) {
            abrirNoNavegador(context, url)
            return
        }

        Toast.makeText(context, "Baixando atualização em segundo plano...", Toast.LENGTH_SHORT).show()
        esperarOFimDownloadManager(context, gerenciador, id)
    }

    private fun esperarOFimDownloadManager(context: Context, gerenciador: DownloadManager, id: Long) {
        val aplicacao = context.applicationContext
        val ouvinte = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val qual = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (qual != id) return
                try {
                    aplicacao.unregisterReceiver(this)
                } catch (e: Throwable) {
                }
                abrirInstalador(aplicacao)
            }
        }

        val filtro = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            aplicacao.registerReceiver(ouvinte, filtro, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            aplicacao.registerReceiver(ouvinte, filtro)
        }
    }

    fun abrirInstalador(context: Context) {
        val pasta = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val arquivo = File(pasta, NOME)
        if (!arquivo.exists() || arquivo.length() < 1024L) {
            Toast.makeText(context, "O arquivo de atualização não foi encontrado ou está incompleto.", Toast.LENGTH_LONG).show()
            return
        }

        val endereco = try {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", arquivo)
        } catch (e: Throwable) {
            Toast.makeText(context, "Não foi possível abrir o instalador do pacote.", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(endereco, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(
                context,
                "Permita a instalação de fontes desconhecidas para este app nas configurações.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun abrirNoNavegador(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Throwable) {
            Toast.makeText(context, "Não foi possível baixar a atualização.", Toast.LENGTH_LONG).show()
        }
    }
}
